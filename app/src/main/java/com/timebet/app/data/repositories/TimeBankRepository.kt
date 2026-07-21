package com.timebet.app.data.repositories

import com.timebet.app.core.database.dao.CasinoRoundDao
import com.timebet.app.core.database.dao.DailyTimeBankDao
import com.timebet.app.core.database.dao.DailyUsageAggregateDao
import com.timebet.app.core.database.dao.SportsPredictionDao
import com.timebet.app.core.database.dao.UserSettingsDao
import com.timebet.app.core.database.entity.*
import com.timebet.app.core.time.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Central repository for all Time Bank operations.
 * Provides a clean API for ViewModels — they never touch engines directly.
 */
class TimeBankRepository(
    private val dailyTimeBankDao: DailyTimeBankDao,
    private val userSettingsDao: UserSettingsDao,
    private val casinoRoundDao: CasinoRoundDao,
    private val sportsPredictionDao: SportsPredictionDao,
    private val dailyUsageAggregateDao: DailyUsageAggregateDao,
    private val timeBankEngine: TimeBankEngine,
    private val coinFlipEngine: CoinFlipEngine,
    private val minesEngine: MinesEngine,
    private val rouletteEngine: RouletteEngine,
    private val blackjackEngine: BlackjackEngine,
    private val crashEngine: CrashEngine,
    private val baccaratEngine: BaccaratEngine,
    private val chickenEngine: ChickenEngine
) {
    // ─── Time Bank ───

    fun observeBalance(): Flow<DailyTimeBankState?> {
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        return dailyTimeBankDao.observeByDate(today).map { entity ->
            entity?.let {
                DailyTimeBankState(
                    date = it.date,
                    baseAllowanceSeconds = it.baseAllowanceSeconds,
                    currentBalanceSeconds = it.currentBalanceSeconds,
                    casinoProfitSeconds = it.casinoProfitSeconds,
                    casinoLossSeconds = it.casinoLossSeconds,
                    sportsProfitSeconds = it.sportsProfitSeconds,
                    totalWinSeconds = it.totalWinSeconds,
                    usedSeconds = it.usedSeconds,
                    netCasinoProfit = it.casinoProfitSeconds - it.casinoLossSeconds,
                    maxDailyBonus = (it.baseAllowanceSeconds * com.timebet.app.util.TimeBetConstants.MAX_DAILY_BONUS_PERCENTAGE).toLong(),
                    todayDate = it.date
                )
            }
        }
    }

    suspend fun getBalance(): Long = timeBankEngine.getBalance()
    suspend fun getDailyBank(): DailyTimeBankState = timeBankEngine.getDailyBank()
    suspend fun deduct(elapsedSeconds: Long): DeductionResult = timeBankEngine.deduct(elapsedSeconds)
    suspend fun ensureDailyReset(): DailyTimeBankState = timeBankEngine.ensureDailyReset()

    // ─── Settings ───

    fun observeSettings(): Flow<UserSettingsEntity?> = userSettingsDao.observe()
    suspend fun getSettings(): UserSettingsEntity? = userSettingsDao.get()
    suspend fun updateAllowance(seconds: Long) = userSettingsDao.updateBaseAllowance(seconds)

    // ─── Casino ───

    suspend fun flipCoin(stakeSeconds: Long, betOnHeads: Boolean): CoinFlipResult {
        return coinFlipEngine.flip(stakeSeconds, betOnHeads)
    }

    /**
     * Initiate a casino round — persist as "initiated" before stake deduction.
     * PRD Section 43: If app crashes mid-round, the stake can be recovered.
     */
    suspend fun initiateCasinoRound(
        gameType: String,
        stakeSeconds: Long,
        metadataJson: String
    ): Long {
        return casinoRoundDao.insert(
            CasinoRoundEntity(
                gameType = gameType,
                stakeSeconds = stakeSeconds,
                result = "pending",
                roundMetadataJson = metadataJson,
                startedAt = System.currentTimeMillis(),
                status = "initiated"
            )
        )
    }

    /**
     * Settle a previously initiated casino round.
     */
    suspend fun finalizeCasinoRound(
        roundId: Long,
        gameType: String,
        stakeSeconds: Long,
        isWin: Boolean,
        profitSeconds: Long,
        metadataJson: String
    ): CasinoSettlementResult {
        val result = timeBankEngine.applyCasinoResult(stakeSeconds, isWin, profitSeconds)
        casinoRoundDao.update(
            CasinoRoundEntity(
                id = roundId,
                gameType = gameType,
                stakeSeconds = stakeSeconds,
                profitSeconds = result.creditedProfit,
                lossSeconds = if (isWin) 0L else stakeSeconds,
                result = when {
                    isWin -> "win"
                    result.capReached -> "win"
                    else -> "loss"
                },
                roundMetadataJson = metadataJson,
                startedAt = System.currentTimeMillis(),
                settledAt = System.currentTimeMillis(),
                status = "settled"
            )
        )
        return result
    }

    suspend fun settleCasinoRound(
        gameType: String,
        stakeSeconds: Long,
        isWin: Boolean,
        profitSeconds: Long,
        metadataJson: String
    ): CasinoSettlementResult {
        val result = timeBankEngine.applyCasinoResult(stakeSeconds, isWin, profitSeconds)
        casinoRoundDao.insert(
            CasinoRoundEntity(
                gameType = gameType,
                stakeSeconds = stakeSeconds,
                profitSeconds = result.creditedProfit,
                lossSeconds = if (isWin) 0L else stakeSeconds,
                result = when {
                    isWin -> "win"
                    result.capReached -> "win"
                    else -> "loss"
                },
                roundMetadataJson = metadataJson,
                startedAt = System.currentTimeMillis(),
                status = "settled"
            )
        )
        return result
    }

    /**
     * Recover unsettled rounds on app restart.
     * PRD Section 43: Refund stake for rounds that were initiated but never settled.
     */
    suspend fun recoverUnsettledRounds() {
        val unsettled = casinoRoundDao.getUnsettledRounds()
        for (round in unsettled) {
            try {
                timeBankEngine.returnStake(round.stakeSeconds)
                casinoRoundDao.update(
                    round.copy(
                        result = "void",
                        settledAt = System.currentTimeMillis(),
                        status = "settled",
                        roundMetadataJson = """{"recovered":true,"original_metadata":${round.roundMetadataJson}}"""
                    )
                )
            } catch (_: Exception) {
                // Mark as void anyway even if refund fails
                casinoRoundDao.update(
                    round.copy(
                        status = "settled",
                        result = "void"
                    )
                )
            }
        }
    }

    fun generateMinePositions(mineCount: Int): Set<Int> = minesEngine.generateMinePositions(mineCount)
    fun calculateMinesMultiplier(mineCount: Int, revealed: Int): Double = minesEngine.calculateMultiplier(mineCount, revealed)
    fun calculateMinesPayout(stake: Long, mineCount: Int, revealed: Int): Long = minesEngine.calculatePayout(stake, mineCount, revealed)

    fun spinRoulette(): RouletteSpinResult = rouletteEngine.spin()
    fun evaluateRouletteBet(bet: RouletteBet, spin: RouletteSpinResult): RouletteBetResult = rouletteEngine.evaluateBet(bet, spin)

    fun dealBlackjack(): BlackjackEngine.BlackjackState = blackjackEngine.deal()
    fun blackjackHit(state: BlackjackEngine.BlackjackState): BlackjackEngine.BlackjackState = blackjackEngine.hit(state)
    fun blackjackStand(state: BlackjackEngine.BlackjackState, stake: Long): BlackjackEngine.BlackjackState = blackjackEngine.stand(state, stake)
    fun blackjackDoubleDown(state: BlackjackEngine.BlackjackState, additionalStake: Long): BlackjackEngine.BlackjackState = blackjackEngine.doubleDown(state, additionalStake)
    fun blackjackProfit(stake: Long): Long = blackjackEngine.calculateBlackjackProfit(stake)

    fun generateCrashPoint(): Double = crashEngine.generateCrashPoint()
    fun crashMultiplierAtTime(elapsedMs: Long, target: Double): Double = crashEngine.calculateMultiplierAtTime(elapsedMs, target)
    fun crashPayout(stake: Long, multiplier: Double): Long = crashEngine.calculatePayout(stake, multiplier)

    // ─── Baccarat ───

    fun dealBaccarat(): BaccaratEngine.BaccaratResult = baccaratEngine.deal()
    fun baccaratPayout(stake: Long, outcome: BaccaratEngine.Outcome, betOn: String): Long =
        baccaratEngine.payout(stake, outcome, betOn)
    fun baccaratIsWin(outcome: BaccaratEngine.Outcome, betOn: String): Boolean =
        baccaratEngine.isWin(outcome, betOn)

    // ─── Chicken ───

    fun createChickenGame(totalLanes: Int, stakeSeconds: Long): ChickenEngine.ChickenGameState =
        ChickenEngine.ChickenGameState(totalLanes = totalLanes, stakeSeconds = stakeSeconds)
    fun tryCrossLane(state: ChickenEngine.ChickenGameState): ChickenEngine.ChickenGameState =
        chickenEngine.tryCrossLane(state)
    fun cashOutChicken(state: ChickenEngine.ChickenGameState, stakeSeconds: Long): ChickenEngine.ChickenGameState =
        chickenEngine.cashOut(state, stakeSeconds)
    fun chickenPayout(stakeSeconds: Long, state: ChickenEngine.ChickenGameState): Long =
        chickenEngine.calculatePayout(stakeSeconds, state)

    // ─── Sports ───

    suspend fun getActiveSportsStake(): Long = sportsPredictionDao.getActiveStakeTotal()
    suspend fun placeSportsPrediction(entity: SportsPredictionEntity): Long {
        timeBankEngine.deductSportsStake(entity.stakeSeconds)
        return sportsPredictionDao.insert(entity)
    }
    suspend fun cancelPrediction(id: Long) {
        val prediction = sportsPredictionDao.getById(id) ?: return
        if (prediction.status == PredictionStatus.PENDING_CANCELABLE) {
            timeBankEngine.returnStake(prediction.stakeSeconds)
            sportsPredictionDao.settle(
                id = id,
                newStatus = PredictionStatus.CANCELLED,
                profit = 0
            )
        }
    }
    fun observeActivePredictions(): Flow<List<SportsPredictionEntity>> = sportsPredictionDao.observeActive()

    // ─── Casino History ───

    fun observeRecentRounds(limit: Int = 100): Flow<List<CasinoRoundEntity>> = casinoRoundDao.observeRecent(limit)
    suspend fun getDailyCasinoStats(startOfDay: Long, endOfDay: Long): CasinoDayStats {
        return CasinoDayStats(
            totalWagered = casinoRoundDao.getTotalWagered(startOfDay, endOfDay),
            totalProfit = casinoRoundDao.getTotalProfit(startOfDay, endOfDay),
            totalLoss = casinoRoundDao.getTotalLoss(startOfDay, endOfDay),
            winCount = casinoRoundDao.getWinCount(startOfDay, endOfDay),
            totalCount = casinoRoundDao.getTotalCount(startOfDay, endOfDay),
            largestWin = casinoRoundDao.getLargestWin(startOfDay, endOfDay),
            largestLoss = casinoRoundDao.getLargestLoss(startOfDay, endOfDay),
            mostPlayedGame = casinoRoundDao.getMostPlayedGame(startOfDay, endOfDay)?.gameType
        )
    }
}

data class CasinoDayStats(
    val totalWagered: Long,
    val totalProfit: Long,
    val totalLoss: Long,
    val winCount: Int,
    val totalCount: Int,
    val largestWin: Long,
    val largestLoss: Long,
    val mostPlayedGame: String?
) {
    val netResult: Long get() = totalProfit - totalLoss
    val winRate: Double get() = if (totalCount > 0) winCount.toDouble() / totalCount else 0.0
}
