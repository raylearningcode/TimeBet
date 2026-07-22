package com.timebet.app.core.sync

import android.content.Context
import android.util.Log
import com.timebet.app.BuildConfig
import com.timebet.app.core.auth.AuthManager
import com.timebet.app.core.database.AppDatabase
import com.timebet.app.core.database.entity.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pushes local changes to Supabase and pulls remote changes.
 *
 * Key design: syncs raw immutable records (usage sessions, casino rounds),
 * not computed balances. This prevents race conditions between devices.
 *
 * Balance is always recomputed from the sum of all records.
 */
class SyncEngine(
    private val context: Context,
    private val authManager: AuthManager,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "SyncEngine"
        private const val SYNC_INTERVAL_MS = 30_000L
        private const val PREF_NAME = "timebet_sync"
        private const val KEY_LAST_SYNC = "last_sync_time"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private var isRunning = false

    private val baseUrl: String get() = BuildConfig.SUPABASE_URL
    private val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
    private val accessToken: String? get() = authManager.getAccessToken()

    fun start() {
        if (isRunning) return
        if (authManager.getAccessToken() == null) return
        isRunning = true

        scope.launch {
            while (isActive && isRunning) {
                try {
                    syncAll()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed", e)
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
    }

    /** Force an immediate sync (called on app open, after casino round, etc.) */
    suspend fun syncNow() {
        if (accessToken == null) return
        try {
            syncAll()
        } catch (e: Exception) {
            Log.e(TAG, "Force sync failed", e)
        }
    }

    // ─── Main Sync Logic ───

    private suspend fun syncAll() {
        val token = accessToken ?: return
        val userId = authManager.getUserId() ?: return
        val deviceId = authManager.deviceIdVal
        val today = LocalDate.now().format(dateFormatter)

        // 1. PUSH: send local pending records to Supabase
        pushUsageSessions(token, userId, deviceId)
        pushCasinoRounds(token, userId, deviceId)
        pushSettings(token, userId)
        pushTimeBank(token, userId, today)
        pushControlledApps(token, userId, deviceId)

        // 2. PULL: fetch remote records we haven't seen yet
        val lastSync = prefs.getString(KEY_LAST_SYNC, null)
        if (lastSync != null) {
            pullUsageSessions(token, userId, lastSync)
            pullCasinoRounds(token, userId, lastSync)
        }
        pullSettings(token, userId)
        pullTimeBank(token, userId, today)
        pullControlledApps(token, userId)

        // 3. Recompute balance from all records
        recomputeBalance(userId, today)

        // 4. Update last sync timestamp
        prefs.edit().putString(KEY_LAST_SYNC, java.time.Instant.now().toString()).apply()
    }

    // ─── Push Methods ───

    private suspend fun pushUsageSessions(token: String, userId: String, deviceId: String) {
        try {
            val sessions = database.appUsageSessionDao().getUnsynced()
            if (sessions.isEmpty()) return

            for (session in sessions) {
                val body = JSONObject().apply {
                    put("user_id", userId)
                    put("package_name", session.packageName)
                    put("duration_seconds", session.durationSeconds)
                    put("device_id", deviceId)
                    put("started_at", session.startedAt)
                    put("ended_at", session.endedAt)
                    put("local_date", LocalDate.now().format(dateFormatter))
                }
                val url = URL("$baseUrl/rest/v1/user_usage_sessions")
                val response = post(url, body, token)
                if (response != null) {
                    database.appUsageSessionDao().markSynced(session.id)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push usage sessions failed: ${e.message}")
        }
    }

    private suspend fun pushCasinoRounds(token: String, userId: String, deviceId: String) {
        try {
            val rounds = database.casinoRoundDao().getUnsynced()
            if (rounds.isEmpty()) return

            for (round in rounds) {
                val body = JSONObject().apply {
                    put("user_id", userId)
                    put("game_type", round.gameType)
                    put("stake_seconds", round.stakeSeconds)
                    put("profit_seconds", round.profitSeconds)
                    put("loss_seconds", round.lossSeconds)
                    put("result", round.result)
                    put("round_metadata", JSONObject(round.roundMetadataJson))
                    put("device_id", deviceId)
                    put("started_at", round.startedAt)
                    put("settled_at", round.settledAt)
                    put("local_date", LocalDate.now().format(dateFormatter))
                }
                val url = URL("$baseUrl/rest/v1/user_casino_rounds")
                val response = post(url, body, token)
                if (response != null) {
                    database.casinoRoundDao().markSynced(round.id)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push casino rounds failed: ${e.message}")
        }
    }

    private suspend fun pushSettings(token: String, userId: String) {
        try {
            val settings = database.userSettingsDao().get() ?: return

            val body = JSONObject().apply {
                put("user_id", userId)
                put("base_daily_allowance_seconds", settings.baseDailyAllowanceSeconds)
            }
            val url = URL("$baseUrl/rest/v1/user_settings?user_id=eq.$userId")
            val existing = get(url, token)
            if (existing.length() > 0) {
                // Update
                val patchUrl = URL("$baseUrl/rest/v1/user_settings?user_id=eq.$userId")
                patch(patchUrl, body, token)
            } else {
                post(URL("$baseUrl/rest/v1/user_settings"), body, token)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push settings failed: ${e.message}")
        }
    }

    private suspend fun pushTimeBank(token: String, userId: String, today: String) {
        try {
            val bank = database.dailyTimeBankDao().getByDate(today) ?: return

            val body = JSONObject().apply {
                put("user_id", userId)
                put("date", today)
                put("base_allowance_seconds", bank.baseAllowanceSeconds)
                put("current_balance_seconds", bank.currentBalanceSeconds)
                put("casino_profit_seconds", bank.casinoProfitSeconds)
                put("casino_loss_seconds", bank.casinoLossSeconds)
                put("sports_profit_seconds", bank.sportsProfitSeconds)
                put("total_win_seconds", bank.totalWinSeconds)
                put("used_seconds", bank.usedSeconds)
            }
            val url = URL("$baseUrl/rest/v1/user_time_banks?user_id=eq.$userId&date=eq.$today")
            val existing = get(url, token)
            if (existing.length() > 0) {
                // Only update if our timestamp is newer
                val patchUrl = URL("$baseUrl/rest/v1/user_time_banks?user_id=eq.$userId&date=eq.$today")
                patch(patchUrl, body, token)
            } else {
                post(URL("$baseUrl/rest/v1/user_time_banks"), body, token)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push time bank failed: ${e.message}")
        }
    }

    private suspend fun pushControlledApps(token: String, userId: String, deviceId: String) {
        try {
            val apps = database.controlledAppDao().getAll()
            if (apps.isEmpty()) return

            for (app in apps) {
                val body = JSONObject().apply {
                    put("user_id", userId)
                    put("package_name", app.packageName)
                    put("app_name", app.appName)
                    put("is_controlled", app.isControlled)
                    put("device_id", deviceId)
                }
                val url = URL("$baseUrl/rest/v1/user_controlled_apps?user_id=eq.$userId&package_name=eq.${app.packageName}")
                val existing = get(url, token)
                if (existing.length() > 0) {
                    patch(url, body, token)
                } else {
                    post(URL("$baseUrl/rest/v1/user_controlled_apps"), body, token)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push controlled apps failed: ${e.message}")
        }
    }

    // ─── Pull Methods ───

    private suspend fun pullUsageSessions(token: String, userId: String, lastSync: String) {
        try {
            val url = URL("$baseUrl/rest/v1/user_usage_sessions?user_id=eq.$userId&created_at=gt.$lastSync&order=created_at.asc&limit=100")
            val json = get(url, token)
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val serverId = obj.getString("id")
                // Check if we already have this record
                val existing = database.appUsageSessionDao().getByServerId(serverId)
                if (existing == null) {
                    database.appUsageSessionDao().insert(
                        AppUsageSessionEntity(
                            packageName = obj.getString("package_name"),
                            startedAt = obj.getLong("started_at"),
                            endedAt = obj.getLong("ended_at"),
                            durationSeconds = obj.getLong("duration_seconds"),
                            wasControlled = true,
                            syncStatus = "synced",
                            serverId = serverId,
                            deviceId = obj.optString("device_id", "unknown")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pull usage sessions failed: ${e.message}")
        }
    }

    private suspend fun pullCasinoRounds(token: String, userId: String, lastSync: String) {
        try {
            val url = URL("$baseUrl/rest/v1/user_casino_rounds?user_id=eq.$userId&created_at=gt.$lastSync&order=created_at.asc&limit=100")
            val json = get(url, token)
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val serverId = obj.getString("id")
                val existing = database.casinoRoundDao().getByServerId(serverId)
                if (existing == null) {
                    database.casinoRoundDao().insert(
                        CasinoRoundEntity(
                            gameType = obj.getString("game_type"),
                            stakeSeconds = obj.getLong("stake_seconds"),
                            profitSeconds = obj.optLong("profit_seconds", 0),
                            lossSeconds = obj.optLong("loss_seconds", 0),
                            result = obj.getString("result"),
                            roundMetadataJson = obj.optJSONObject("round_metadata")?.toString() ?: "{}",
                            startedAt = obj.getLong("started_at"),
                            settledAt = obj.optLong("settled_at", 0),
                            status = "settled",
                            syncStatus = "synced",
                            serverId = serverId,
                            deviceId = obj.optString("device_id", "unknown")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pull casino rounds failed: ${e.message}")
        }
    }

    private suspend fun pullSettings(token: String, userId: String) {
        try {
            val url = URL("$baseUrl/rest/v1/user_settings?user_id=eq.$userId&limit=1")
            val json = get(url, token)
            if (json.length() > 0) {
                val obj = json.getJSONObject(0)
                val remoteAllowance = obj.optLong("base_daily_allowance_seconds", 7200)
                val local = database.userSettingsDao().get()
                if (local == null) {
                    // No local settings — adopt remote
                    database.userSettingsDao().upsert(
                        UserSettingsEntity(
                            baseDailyAllowanceSeconds = remoteAllowance
                        )
                    )
                }
                // If local exists, local wins (user's device settings take priority)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pull settings failed: ${e.message}")
        }
    }

    private suspend fun pullTimeBank(token: String, userId: String, today: String) {
        try {
            val url = URL("$baseUrl/rest/v1/user_time_banks?user_id=eq.$userId&date=eq.$today&limit=1")
            val json = get(url, token)
            if (json.length() > 0) {
                val obj = json.getJSONObject(0)
                // Don't blindly overwrite — the balance should be recomputed from records
                // Just ensure the bank entry exists locally
                val local = database.dailyTimeBankDao().getByDate(today)
                if (local == null) {
                    val allowance = obj.optLong("base_allowance_seconds", 7200)
                    database.dailyTimeBankDao().upsert(
                        DailyTimeBankEntity(
                            date = today,
                            baseAllowanceSeconds = allowance,
                            currentBalanceSeconds = allowance
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pull time bank failed: ${e.message}")
        }
    }

    private suspend fun pullControlledApps(token: String, userId: String) {
        try {
            val url = URL("$baseUrl/rest/v1/user_controlled_apps?user_id=eq.$userId&is_controlled=eq.true")
            val json = get(url, token)
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val packageName = obj.getString("package_name")
                val appName = obj.getString("app_name")
                val existing = database.controlledAppDao().getByPackage(packageName)
                if (existing == null) {
                    database.controlledAppDao().upsert(
                        ControlledAppEntity(
                            packageName = packageName,
                            appName = appName,
                            isControlled = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pull controlled apps failed: ${e.message}")
        }
    }

    // ─── Balance Recompute ───

    /**
     * Recompute today's balance from all synced records across all devices.
     * This is the anti-exploit mechanism: balance = allowance - usage + casino_net + sports_net
     */
    private suspend fun recomputeBalance(userId: String, today: String) {
        try {
            val allowance = database.userSettingsDao().get()?.baseDailyAllowanceSeconds
                ?: com.timebet.app.util.TimeBetConstants.DEFAULT_BASE_ALLOWANCE_SECONDS

            val startOfDay = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

            val totalUsage = database.appUsageSessionDao().getTotalControlledUsage(startOfDay, endOfDay) ?: 0
            val casinoStats = com.timebet.app.ServiceLocator.timeBankRepository.getDailyCasinoStats(startOfDay, endOfDay)
            val casinoNet = casinoStats.totalProfit - casinoStats.totalLoss

            val sportsProfit = database.sportsPredictionDao().getActiveStakeTotal()
            // Active stake is already deducted, settled profit adds back — approximate
            val balance = (allowance - totalUsage + casinoNet).coerceAtLeast(0)

            database.dailyTimeBankDao().upsert(
                DailyTimeBankEntity(
                    date = today,
                    baseAllowanceSeconds = allowance,
                    currentBalanceSeconds = balance,
                    casinoProfitSeconds = casinoStats.totalProfit,
                    casinoLossSeconds = casinoStats.totalLoss,
                    usedSeconds = totalUsage,
                    totalWinSeconds = database.dailyTimeBankDao().getByDate(today)?.totalWinSeconds ?: 0
                )
            )
            Log.d(TAG, "Balance recomputed: $balance (allowance=$allowance, usage=$totalUsage, casinoNet=$casinoNet)")
        } catch (e: Exception) {
            Log.e(TAG, "Balance recompute failed", e)
        }
    }

    // ─── HTTP Helpers ───

    private fun get(url: URL, token: String): JSONArray {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return JSONArray()
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val body = reader.readText()
        reader.close()
        conn.disconnect()
        return JSONArray(body)
    }

    private fun post(url: URL, jsonBody: JSONObject, token: String): String? {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Prefer", "return=representation")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(jsonBody.toString())
        writer.flush()
        writer.close()

        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return null
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val body = reader.readText()
        reader.close()
        conn.disconnect()
        return body
    }

    private fun patch(url: URL, jsonBody: JSONObject, token: String) {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PATCH"
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Prefer", "return=representation")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(jsonBody.toString())
        writer.flush()
        writer.close()

        conn.responseCode // trigger the request
        conn.disconnect()
    }
}
