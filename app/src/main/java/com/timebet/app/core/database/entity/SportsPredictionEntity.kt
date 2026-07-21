package com.timebet.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Statuses per PRD Section 33.6:
 * - pending_cancelable: same-day, can be cancelled
 * - pending_locked: date changed, locked in
 * - won: settled as win
 * - lost: settled as loss
 * - void: match voided, stake returned
 * - cancelled: user cancelled same-day
 */
object PredictionStatus {
    const val PENDING_CANCELABLE = "pending_cancelable"
    const val PENDING_LOCKED = "pending_locked"
    const val WON = "won"
    const val LOST = "lost"
    const val VOID = "void"
    const val CANCELLED = "cancelled"
}

object MarketType {
    const val HOME_DRAW_AWAY = "home_draw_away"
    const val OVER_UNDER_15 = "over_under_1_5"
    const val OVER_UNDER_25 = "over_under_2_5"
    const val BOTH_TEAMS_TO_SCORE = "both_teams_to_score"
}

@Entity(tableName = "sports_predictions")
data class SportsPredictionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val providerEventId: String,
    val sport: String = "football",
    val competition: String,
    val homeTeam: String,
    val awayTeam: String,
    val marketType: String, // MarketType constants
    val selection: String, // e.g., "home", "draw", "away", "over", "under", "yes", "no"
    val oddsAtPlacement: Double,
    val stakeSeconds: Long,
    val potentialProfitSeconds: Long, // stake * (odds - 1), only profit portion
    val placedAt: Long,
    val placementLocalDate: String, // ISO date when placed
    val status: String = PredictionStatus.PENDING_CANCELABLE,
    val lockedAt: Long? = null,
    val settledAt: Long? = null,
    val settlementProfitSeconds: Long = 0,
    val providerPayloadJson: String = "{}"
)
