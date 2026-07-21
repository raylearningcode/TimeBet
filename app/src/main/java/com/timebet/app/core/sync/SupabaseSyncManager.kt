package com.timebet.app.core.sync

import android.content.Context
import android.util.Log
import com.timebet.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles Supabase backend communication via PostgREST API.
 *
 * Uses the Supabase anon key from BuildConfig for all requests.
 * All sports data flows through the Supabase cache (never API-Football directly).
 */
class SupabaseSyncManager(context: Context) {

    companion object {
        private const val TAG = "SupabaseSync"
    }

    private val baseUrl = BuildConfig.SUPABASE_URL
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    /**
     * Fetch upcoming fixtures from the Supabase cache.
     */
    suspend fun fetchFixtures(status: String = "scheduled", limit: Int = 20): List<FixtureResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/sports_fixtures?status=eq.$status&order=kickoff_time.asc&limit=$limit")
            val json = get(url)
            parseFixtures(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch fixtures: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch odds for a specific fixture.
     */
    suspend fun fetchOdds(fixtureId: String): List<MarketResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/sports_odds?fixture_id=eq.$fixtureId")
            val json = get(url)
            parseOdds(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch odds for $fixtureId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch odds for multiple fixtures in a single request.
     * Uses the `in.()` PostgREST filter for batch queries.
     */
    suspend fun fetchOddsBatch(fixtureIds: List<String>): Map<String, List<MarketResponse>> = withContext(Dispatchers.IO) {
        if (fixtureIds.isEmpty()) return@withContext emptyMap()
        try {
            val ids = fixtureIds.joinToString(",") { "\"$it\"" }
            val url = URL("$baseUrl/rest/v1/sports_odds?fixture_id=in.($ids)&order=fixture_id.asc")
            val json = get(url)
            val byFixture = mutableMapOf<String, List<MarketResponse>>()
            for (fid in fixtureIds) {
                val markets = mutableMapOf<String, MutableList<SelectionResponse>>()
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    if (obj.getString("fixture_id") == fid) {
                        val mt = obj.getString("market_type")
                        markets.getOrPut(mt) { mutableListOf() }
                            .add(SelectionResponse(obj.getString("selection"), obj.getDouble("odds")))
                    }
                }
                byFixture[fid] = markets.map { (type, sels) -> MarketResponse(type, sels) }
            }
            byFixture
        } catch (e: Exception) {
            Log.w(TAG, "Failed to batch fetch odds: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Place a sports prediction on the backend for server-side settlement.
     */
    suspend fun placePrediction(request: PredictionRequest): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/sports_predictions")
            val body = JSONObject().apply {
                put("provider_event_id", request.eventId)
                put("competition", request.competition)
                put("home_team", request.homeTeam)
                put("away_team", request.awayTeam)
                put("market_type", request.marketType)
                put("selection", request.selection)
                put("odds_at_placement", request.oddsAtPlacement)
                put("stake_seconds", request.stakeSeconds)
                put("potential_profit_seconds", request.potentialProfitSeconds)
                put("placement_local_date", java.time.LocalDate.now().toString())
            }
            val response = post(url, body)
            if (response != null) {
                JSONArray(response).getJSONObject(0).optString("id", null)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to place prediction: ${e.message}")
            null
        }
    }

    /**
     * Check for settled predictions.
     */
    suspend fun checkSettlements(): List<SettlementResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/sports_predictions?status=in.(won,lost,void)&order=settled_at.desc&limit=20")
            val json = get(url)
            parseSettlements(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check settlements: ${e.message}")
            emptyList()
        }
    }

    suspend fun trackEvent(eventName: String, properties: Map<String, String> = emptyMap()) {
        try {
            val url = URL("$baseUrl/rest/v1/analytics_events")
            val body = JSONObject().apply {
                put("event", eventName)
                put("properties", JSONObject(properties))
                put("client_timestamp", System.currentTimeMillis())
            }
            post(url, body)
        } catch (e: Exception) {
            Log.w(TAG, "Analytics event dropped: ${e.message}")
        }
    }

    // ─── HTTP helpers ───

    private fun get(url: URL): JSONArray {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Authorization", "Bearer $anonKey")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw Exception("HTTP $code")
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val body = reader.readText()
        reader.close()
        conn.disconnect()
        return JSONArray(body)
    }

    private fun post(url: URL, jsonBody: JSONObject): String? {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Authorization", "Bearer $anonKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
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
            throw Exception("HTTP $code")
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val body = reader.readText()
        reader.close()
        conn.disconnect()
        return body
    }

    // ─── Parsers ───

    private fun parseFixtures(json: JSONArray): List<FixtureResponse> {
        val fixtures = mutableListOf<FixtureResponse>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            fixtures.add(
                FixtureResponse(
                    id = obj.getString("id"),
                    sport = obj.optString("sport", "football"),
                    competition = obj.getString("competition"),
                    homeTeam = obj.getString("home_team"),
                    awayTeam = obj.getString("away_team"),
                    kickoffTime = obj.getString("kickoff_time"),
                    markets = emptyList() // odds fetched separately
                )
            )
        }
        return fixtures
    }

    private fun parseOdds(json: JSONArray): List<MarketResponse> {
        val markets = mutableMapOf<String, MutableList<SelectionResponse>>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val marketType = obj.getString("market_type")
            val selection = obj.getString("selection")
            val odds = obj.getDouble("odds")
            markets.getOrPut(marketType) { mutableListOf() }
                .add(SelectionResponse(name = selection, odds = odds))
        }
        val result = mutableListOf<MarketResponse>()
        for ((type, selections) in markets) {
            result.add(MarketResponse(type = type, selections = selections))
        }
        return result
    }

    private fun parseSettlements(json: JSONArray): List<SettlementResponse> {
        val settlements = mutableListOf<SettlementResponse>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val settlement = SettlementResponse(
                predictionId = obj.getString("id"),
                status = obj.getString("status"),
                profitSeconds = obj.optLong("settlement_profit_seconds", 0)
            )
            settlements.add(settlement)
        }
        return settlements
    }
}

// ─── Data classes for API communication ───

data class FixtureResponse(
    val id: String,
    val sport: String,
    val competition: String,
    val homeTeam: String,
    val awayTeam: String,
    val kickoffTime: String,
    val markets: List<MarketResponse>
)

data class MarketResponse(
    val type: String,
    val selections: List<SelectionResponse>
)

data class SelectionResponse(
    val name: String,
    val odds: Double
)

data class PredictionRequest(
    val eventId: String,
    val marketType: String,
    val selection: String,
    val oddsAtPlacement: Double,
    val stakeSeconds: Long,
    val potentialProfitSeconds: Long,
    val placedAt: Long,
    val competition: String = "",
    val homeTeam: String = "",
    val awayTeam: String = ""
)

data class PredictionResponse(
    val id: String
)

data class SettlementResponse(
    val predictionId: String,
    val status: String,
    val profitSeconds: Long = 0
)
