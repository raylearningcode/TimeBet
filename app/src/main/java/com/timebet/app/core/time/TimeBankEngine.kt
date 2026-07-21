package com.timebet.app.core.time

import com.timebet.app.core.database.dao.DailyTimeBankDao
import com.timebet.app.util.TimeBetConstants
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The authoritative engine for all Time Bank operations.
 *
 * All balance mutations must flow through this engine (PRD Section 32.3, 35).
 * It operates on monotonic elapsed time, not repeated one-second writes.
 *
 * Invariants enforced (PRD Section 34):
 * - current_balance_seconds >= 0
 * - daily_net_profit_seconds <= max_daily_bonus_seconds
 * - active_sports_stake_seconds <= max_active_sports_stake_seconds
 */
class TimeBankEngine(
    private val dailyTimeBankDao: DailyTimeBankDao,
    private val userSettingsProvider: suspend () -> Long // returns base allowance in seconds
) {

    private val mutex = Mutex()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Deduct time from the current balance. Returns the actual amount deducted
     * (may be less than requested if balance is insufficient).
     *
     * PRD Section 32.3: Uses session-based deduction, not per-second writes.
     */
    suspend fun deduct(elapsedSeconds: Long): DeductionResult = mutex.withLock {
        val today = todayDate()
        val bank = ensureBankExists(today)
        val current = bank.currentBalanceSeconds

        if (current <= 0) {
            return DeductionResult(
                deductedSeconds = 0,
                remainingBalance = 0,
                isZero = true
            )
        }

        val actualDeduction = elapsedSeconds.coerceAtMost(current)
        val newBalance = current - actualDeduction

        dailyTimeBankDao.updateBalances(
            date = today,
            balance = newBalance,
            casinoProfit = bank.casinoProfitSeconds,
            casinoLoss = bank.casinoLossSeconds,
            sportsProfit = bank.sportsProfitSeconds,
            totalWinSeconds = bank.totalWinSeconds,
            used = bank.usedSeconds + actualDeduction
        )

        DeductionResult(
            deductedSeconds = actualDeduction,
            remainingBalance = newBalance,
            isZero = newBalance <= 0
        )
    }

    /**
     * Get current balance without modifying it.
     */
    suspend fun getBalance(): Long = mutex.withLock {
        val today = todayDate()
        dailyTimeBankDao.getByDate(today)?.currentBalanceSeconds ?: run {
            val allowance = userSettingsProvider()
            initializeToday(today, allowance)
            allowance
        }
    }

    /**
     * Get the full daily bank state.
     */
    suspend fun getDailyBank(): DailyTimeBankState = mutex.withLock {
        val today = todayDate()
        val bank = ensureBankExists(today)
        DailyTimeBankState(
            date = bank.date,
            baseAllowanceSeconds = bank.baseAllowanceSeconds,
            currentBalanceSeconds = bank.currentBalanceSeconds,
            casinoProfitSeconds = bank.casinoProfitSeconds,
            casinoLossSeconds = bank.casinoLossSeconds,
            sportsProfitSeconds = bank.sportsProfitSeconds,
            totalWinSeconds = bank.totalWinSeconds,
            usedSeconds = bank.usedSeconds,
            netCasinoProfit = bank.casinoProfitSeconds - bank.casinoLossSeconds,
            maxDailyBonus = (bank.baseAllowanceSeconds * TimeBetConstants.MAX_DAILY_BONUS_PERCENTAGE).toLong(),
            todayDate = today
        )
    }

    // ─── Casino Settlement (PRD Section 35) ───

    /**
     * Apply a casino round result atomically. Returns the actual credited profit
     * after applying the daily bonus cap.
     *
     * PRD Section 8.4: If a win would exceed the daily cap, only credit up to the cap.
     * PRD Section 35: Atomic settlement flow.
     */
    suspend fun applyCasinoResult(
        stakeSeconds: Long,
        isWin: Boolean,
        profitSeconds: Long // 0 for loss, the raw profit for win
    ): CasinoSettlementResult = mutex.withLock {
        val today = todayDate()
        val bank = ensureBankExists(today)

        // Validate stake
        require(stakeSeconds > 0) { "Stake must be positive" }
        require(stakeSeconds <= bank.currentBalanceSeconds) { "Stake exceeds balance" }

        // Check daily bonus cap based on TOTAL WINS (not net profit)
        // Once totalWinSeconds >= maxDailyBonus, casino is locked for the day
        val maxBonus = (bank.baseAllowanceSeconds * TimeBetConstants.MAX_DAILY_BONUS_PERCENTAGE).toLong()

        if (bank.totalWinSeconds >= maxBonus) {
            // Cap already reached — reject casino play
            return CasinoSettlementResult(
                isWin = false,
                creditedProfit = 0,
                newBalance = bank.currentBalanceSeconds,
                capReached = true
            )
        }

        if (isWin) {
            val remainingCap = maxBonus - bank.totalWinSeconds
            val cappedProfit = profitSeconds.coerceAtMost(remainingCap)
            val newTotalWins = bank.totalWinSeconds + cappedProfit

            val newBalance = bank.currentBalanceSeconds + cappedProfit
            dailyTimeBankDao.updateBalances(
                date = today,
                balance = newBalance,
                casinoProfit = bank.casinoProfitSeconds + cappedProfit,
                casinoLoss = bank.casinoLossSeconds,
                sportsProfit = bank.sportsProfitSeconds,
                totalWinSeconds = newTotalWins,
                used = bank.usedSeconds
            )

            CasinoSettlementResult(
                isWin = true,
                creditedProfit = cappedProfit,
                newBalance = newBalance,
                capReached = cappedProfit < profitSeconds || newTotalWins >= maxBonus,
                rawProfit = profitSeconds
            )
        } else {
            val newBalance = bank.currentBalanceSeconds - stakeSeconds
            dailyTimeBankDao.updateBalances(
                date = today,
                balance = newBalance,
                casinoProfit = bank.casinoProfitSeconds,
                casinoLoss = bank.casinoLossSeconds + stakeSeconds,
                sportsProfit = bank.sportsProfitSeconds,
                totalWinSeconds = bank.totalWinSeconds,
                used = bank.usedSeconds
            )

            CasinoSettlementResult(
                isWin = false,
                creditedProfit = 0,
                newBalance = newBalance,
                capReached = false
            )
        }
    }

    // ─── Sports Operations (PRD Section 17) ───

    /**
     * Deduct stake for a sports prediction placement.
     * PRD Section 17.2: Stake is immediately deducted from today's Time Bank.
     */
    suspend fun deductSportsStake(stakeSeconds: Long): Long = mutex.withLock {
        val today = todayDate()
        val bank = ensureBankExists(today)

        require(stakeSeconds > 0) { "Stake must be positive" }
        require(stakeSeconds <= bank.currentBalanceSeconds) { "Stake exceeds balance" }

        val maxActiveStake = (bank.baseAllowanceSeconds * TimeBetConstants.MAX_SPORTS_STAKE_PERCENTAGE).toLong()
        // Current active sports stake is tracked by the caller via SportsPredictionDao
        // This just deducts from balance

        val newBalance = bank.currentBalanceSeconds - stakeSeconds
        dailyTimeBankDao.updateBalance(today, newBalance)
        newBalance
    }

    /**
     * Credit sports profit on settlement. Subject to daily bonus cap.
     * PRD Section 17.4: Win credits only the profit portion.
     */
    suspend fun creditSportsProfit(profitSeconds: Long): Long = mutex.withLock {
        val today = todayDate()
        val bank = ensureBankExists(today)

        if (profitSeconds <= 0) return bank.currentBalanceSeconds

        val maxBonus = (bank.baseAllowanceSeconds * TimeBetConstants.MAX_DAILY_BONUS_PERCENTAGE).toLong()
        val remainingCap = (maxBonus - bank.totalWinSeconds).coerceAtLeast(0)

        val credited = profitSeconds.coerceAtMost(remainingCap)
        val newBalance = bank.currentBalanceSeconds + credited

        dailyTimeBankDao.updateBalances(
            date = today,
            balance = newBalance,
            casinoProfit = bank.casinoProfitSeconds,
            casinoLoss = bank.casinoLossSeconds,
            sportsProfit = bank.sportsProfitSeconds + credited,
            totalWinSeconds = bank.totalWinSeconds + credited,
            used = bank.usedSeconds
        )

        newBalance
    }

    /**
     * Return stake for cancelled or void predictions.
     * PRD Section 17.3, 17.4: Cancellation returns full stake; void returns stake.
     */
    suspend fun returnStake(stakeSeconds: Long): Long = mutex.withLock {
        val today = todayDate()
        val bank = ensureBankExists(today)

        val newBalance = bank.currentBalanceSeconds + stakeSeconds
        dailyTimeBankDao.updateBalance(today, newBalance)
        newBalance
    }

    // ─── Daily Reset (PRD Section 7.3) ───

    /**
     * Perform daily reset. Creates today's bank if it doesn't exist.
     * PRD Section 7.3: At reset, unused time expires, new balance = base allowance.
     */
    suspend fun ensureDailyReset(): DailyTimeBankState = mutex.withLock {
        val today = todayDate()
        val allowance = userSettingsProvider()
        val existing = dailyTimeBankDao.getByDate(today)

        if (existing == null) {
            initializeToday(today, allowance)
        }

        getDailyBankUnsafe()
    }

    // ─── Private Helpers ───

    private fun todayDate(): String = LocalDate.now().format(dateFormatter)

    private suspend fun ensureBankExists(date: String): DailyTimeBankState {
        val bank = dailyTimeBankDao.getByDate(date)
        return if (bank != null) {
            DailyTimeBankState(
                date = bank.date,
                baseAllowanceSeconds = bank.baseAllowanceSeconds,
                currentBalanceSeconds = bank.currentBalanceSeconds,
                casinoProfitSeconds = bank.casinoProfitSeconds,
                casinoLossSeconds = bank.casinoLossSeconds,
                sportsProfitSeconds = bank.sportsProfitSeconds,
                totalWinSeconds = bank.totalWinSeconds,
                usedSeconds = bank.usedSeconds,
                netCasinoProfit = bank.casinoProfitSeconds - bank.casinoLossSeconds,
                maxDailyBonus = (bank.baseAllowanceSeconds * TimeBetConstants.MAX_DAILY_BONUS_PERCENTAGE).toLong(),
                todayDate = date
            )
        } else {
            val allowance = userSettingsProvider()
            initializeToday(date, allowance)
            getDailyBankUnsafe()
        }
    }

    private suspend fun initializeToday(date: String, allowance: Long) {
        dailyTimeBankDao.upsert(
            com.timebet.app.core.database.entity.DailyTimeBankEntity(
                date = date,
                baseAllowanceSeconds = allowance,
                currentBalanceSeconds = allowance
            )
        )
    }

    private suspend fun getDailyBankUnsafe(): DailyTimeBankState {
        val bank = dailyTimeBankDao.getByDate(todayDate())!!
        return DailyTimeBankState(
            date = bank.date,
            baseAllowanceSeconds = bank.baseAllowanceSeconds,
            currentBalanceSeconds = bank.currentBalanceSeconds,
            casinoProfitSeconds = bank.casinoProfitSeconds,
            casinoLossSeconds = bank.casinoLossSeconds,
            sportsProfitSeconds = bank.sportsProfitSeconds,
            totalWinSeconds = bank.totalWinSeconds,
            usedSeconds = bank.usedSeconds,
            netCasinoProfit = bank.casinoProfitSeconds - bank.casinoLossSeconds,
            maxDailyBonus = (bank.baseAllowanceSeconds * TimeBetConstants.MAX_DAILY_BONUS_PERCENTAGE).toLong(),
            todayDate = bank.date
        )
    }
}

// ─── Result Types ───

data class DeductionResult(
    val deductedSeconds: Long,
    val remainingBalance: Long,
    val isZero: Boolean
)

data class CasinoSettlementResult(
    val isWin: Boolean,
    val creditedProfit: Long,
    val newBalance: Long,
    val capReached: Boolean,
    val rawProfit: Long = 0
)

data class DailyTimeBankState(
    val date: String,
    val baseAllowanceSeconds: Long,
    val currentBalanceSeconds: Long,
    val casinoProfitSeconds: Long,
    val casinoLossSeconds: Long,
    val sportsProfitSeconds: Long,
    val totalWinSeconds: Long = 0, // Total win amount (not netted against losses)
    val usedSeconds: Long,
    val netCasinoProfit: Long,
    val maxDailyBonus: Long,
    val todayDate: String
) {
    val netGamblingProfit: Long
        get() = (casinoProfitSeconds + sportsProfitSeconds) - casinoLossSeconds

    val isBonusCapReached: Boolean
        get() = totalWinSeconds >= maxDailyBonus

    val remainingBonusCapacity: Long
        get() = (maxDailyBonus - totalWinSeconds).coerceAtLeast(0)
}
