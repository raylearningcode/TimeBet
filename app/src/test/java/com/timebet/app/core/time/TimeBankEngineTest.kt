package com.timebet.app.core.time

import com.timebet.app.core.database.dao.DailyTimeBankDao
import com.timebet.app.core.database.entity.DailyTimeBankEntity
import com.timebet.app.util.TimeBetConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Unit tests for TimeBankEngine — PRD Section 42.1.
 *
 * Tests: deduction, zero clamp, daily reset, profit cap, sports stake limit.
 */
class TimeBankEngineTest {

    private lateinit var engine: TimeBankEngine
    private lateinit var fakeDao: FakeDailyTimeBankDao
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val today = LocalDate.now().format(dateFormatter)
    private val baseAllowance = 120 * 60L // 2 hours

    @Before
    fun setup() {
        fakeDao = FakeDailyTimeBankDao()
        engine = TimeBankEngine(
            dailyTimeBankDao = fakeDao,
            userSettingsProvider = { baseAllowance }
        )
    }

    @Test
    fun `deduct reduces balance by exact amount`() = runTest {
        val result = engine.deduct(60)
        assertEquals(60, result.deductedSeconds)
        assertEquals(baseAllowance - 60, result.remainingBalance)
        assertFalse(result.isZero)
    }

    @Test
    fun `deduct cannot reduce balance below zero`() = runTest {
        // Deduct full balance
        engine.deduct(baseAllowance)

        // Try to deduct more
        val result = engine.deduct(100)
        assertEquals(0, result.deductedSeconds)
        assertEquals(0, result.remainingBalance)
        assertTrue(result.isZero)
    }

    @Test
    fun `deduct partial when overdrawing`() = runTest {
        engine.deduct(baseAllowance - 30) // 30 seconds left

        val result = engine.deduct(100) // Try to deduct 100
        assertEquals(30, result.deductedSeconds)
        assertEquals(0, result.remainingBalance)
        assertTrue(result.isZero)
    }

    @Test
    fun `getBalance returns base allowance on first call`() = runTest {
        val balance = engine.getBalance()
        assertEquals(baseAllowance, balance)
    }

    @Test
    fun `ensureDailyReset creates today bank when missing`() = runTest {
        // Don't deduct anything — simulate no today entry yet
        // Insert yesterday's data to show we're past a day boundary
        val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
        fakeDao.upsert(
            DailyTimeBankEntity(
                date = yesterday,
                baseAllowanceSeconds = baseAllowance,
                currentBalanceSeconds = baseAllowance - 3600,
                usedSeconds = 3600
            )
        )

        // ensureDailyReset should create today's bank with fresh allowance
        val state = engine.ensureDailyReset()
        assertEquals(today, state.date)
        assertEquals(baseAllowance, state.currentBalanceSeconds)
        assertEquals(0, state.usedSeconds)
    }

    @Test
    fun `applyCasinoResult win increases balance`() = runTest {
        val result = engine.applyCasinoResult(
            stakeSeconds = 600,
            isWin = true,
            profitSeconds = 570 // ~1.95x payout on 600s (profit = 570)
        )
        assertTrue(result.isWin)
        assertEquals(570, result.creditedProfit)
        assertEquals(baseAllowance + 570, result.newBalance)
    }

    @Test
    fun `applyCasinoResult loss decreases balance`() = runTest {
        val result = engine.applyCasinoResult(
            stakeSeconds = 600,
            isWin = false,
            profitSeconds = 0
        )
        assertFalse(result.isWin)
        assertEquals(0, result.creditedProfit)
        assertEquals(baseAllowance - 600, result.newBalance)
    }

    @Test
    fun `applyCasinoResult rejects when stake exceeds balance`() = runTest {
        try {
            engine.applyCasinoResult(
                stakeSeconds = baseAllowance + 1,
                isWin = true,
                profitSeconds = 100
            )
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `profit cap prevents exceeding 75 percent of base allowance`() = runTest {
        val maxBonus = (baseAllowance * TimeBetConstants.MAX_DAILY_BONUS_PERCENTAGE).toLong()

        // Win up to the cap
        val result1 = engine.applyCasinoResult(
            stakeSeconds = 600,
            isWin = true,
            profitSeconds = maxBonus
        )
        assertEquals(maxBonus, result1.creditedProfit)

        // Try to win more — should cap
        val result2 = engine.applyCasinoResult(
            stakeSeconds = 600,
            isWin = true,
            profitSeconds = 1000
        )
        assertTrue(result2.capReached)
        assertEquals(0, result2.creditedProfit)
    }

    @Test
    fun `applyCasinoResult rejects when cap already reached`() = runTest {
        val maxBonus = (baseAllowance * TimeBetConstants.MAX_DAILY_BONUS_PERCENTAGE).toLong()

        engine.applyCasinoResult(600, isWin = true, profitSeconds = maxBonus)

        val result = engine.applyCasinoResult(600, isWin = true, profitSeconds = 100)
        assertTrue(result.capReached)
        assertEquals(0, result.creditedProfit)
    }
}

/**
 * In-memory fake DAO for testing TimeBankEngine without Room.
 */
class FakeDailyTimeBankDao : DailyTimeBankDao {
    private val store = mutableMapOf<String, DailyTimeBankEntity>()

    override fun observeByDate(date: String): Flow<DailyTimeBankEntity?> = flowOf(store[date])
    override suspend fun getByDate(date: String): DailyTimeBankEntity? = store[date]
    override suspend fun upsert(bank: DailyTimeBankEntity) { store[bank.date] = bank }
    override suspend fun updateBalances(
        date: String, balance: Long, casinoProfit: Long, casinoLoss: Long,
        sportsProfit: Long, totalWinSeconds: Long, used: Long, updatedAt: Long
    ) {
        store[date]?.let { existing ->
            store[date] = existing.copy(
                currentBalanceSeconds = balance,
                casinoProfitSeconds = casinoProfit,
                casinoLossSeconds = casinoLoss,
                sportsProfitSeconds = sportsProfit,
                totalWinSeconds = totalWinSeconds,
                usedSeconds = used,
                updatedAt = updatedAt
            )
        }
    }
    override suspend fun updateBalance(date: String, balance: Long, updatedAt: Long) {
        store[date]?.let { existing ->
            store[date] = existing.copy(currentBalanceSeconds = balance, updatedAt = updatedAt)
        }
    }
    override suspend fun getRange(fromDate: String, toDate: String): List<DailyTimeBankEntity> =
        store.values.filter { it.date in fromDate..toDate }
    override suspend fun getRecent(limit: Int): List<DailyTimeBankEntity> =
        store.values.toList().takeLast(limit)
}
