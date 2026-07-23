package com.timebet.app.core.quests

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uses Gemini API (free tier) to generate personalized quest suggestions
 * based on user's actual step and usage data.
 *
 * Free Gemini API endpoint: generativelanguage.googleapis.com
 * Model: gemini-1.5-flash (free tier, 15 RPM)
 */
object GeminiQuestAdvisor {

    // Free Gemini API key — user should set this in build config or settings
    // Using gemini-1.5-flash which has a generous free tier
    private val GEMINI_API_KEY: String get() = com.timebet.app.BuildConfig.GEMINI_API_KEY
    private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    /**
     * Generate AI-powered quest suggestions based on user data.
     *
     * @param context Android context
     * @param avgSteps User's 14-day average steps
     * @param stepTrend "increasing", "decreasing", or "stable"
     * @param topApps List of (appName, dailyAvgMinutes) for the most-used apps
     * @return List of (type, title, targetValue, rewardMinutes) suggestions
     */
    suspend fun getSuggestions(
        context: Context,
        avgSteps: Double,
        stepTrend: String,
        topApps: List<Pair<String, Long>>
    ): List<GeminiQuestSuggestion> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildPrompt(avgSteps, stepTrend, topApps)
            val response = callGemini(prompt)
            parseSuggestions(response)
        } catch (e: Exception) {
            // Fallback: return empty — caller will use statistical generator
            emptyList()
        }
    }

    private fun buildPrompt(
        avgSteps: Double,
        stepTrend: String,
        topApps: List<Pair<String, Long>>
    ): String {
        val appsDesc = topApps.joinToString("\n") { (name, mins) ->
            "  - $name: ${mins}min/day average"
        }

        return """
You are a productivity coach helping reduce phone addiction. Based on this user's real data, suggest 3 daily quests that are challenging but achievable:

USER DATA:
- 14-day average steps: ${avgSteps.toInt()} steps/day
- Step trend: $stepTrend
- Most used apps:
$appsDesc

Generate exactly 3 quests in this JSON format:
```json
[
  {
    "type": "step",
    "title": "creative motivating title",
    "target_steps": 5000,
    "reward_minutes": 12,
    "reason": "why this target fits the user"
  },
  {
    "type": "discipline",
    "title": "creative title for app limit",
    "target_app": "app name",
    "target_minutes": 30,
    "reward_minutes": 15,
    "reason": "why this reduction makes sense"
  },
  {
    "type": "combo",
    "title": "creative title combining both",
    "target_steps": 5000,
    "target_app": "app name",
    "target_minutes": 30,
    "reward_minutes": 20,
    "reason": "synergy between movement and discipline"
  }
]
```

Rules:
- Step targets should be 10-30% above user's average (stretch but achievable)
- App limits should be 15-35% below user's current usage
- Rewards: step (5-20 min), discipline (10-25 min), combo (15-30 min)
- Titles should be motivating and fun, not boring
- Total rewards across all 3 quests should not exceed 45 minutes
- Respond ONLY with the JSON array, no other text.
""".trimIndent()
    }

    private fun callGemini(prompt: String): String {
        val url = URL("$GEMINI_URL?key=$GEMINI_API_KEY")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 512)
            })
        }

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(body.toString())
        writer.flush()
        writer.close()

        val code = conn.responseCode
        val responseText = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
        }
        conn.disconnect()

        if (code !in 200..299) throw Exception("Gemini API error: $responseText")

        // Extract text from Gemini response
        val json = JSONObject(responseText)
        val candidates = json.getJSONArray("candidates")
        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        return parts.getJSONObject(0).getString("text")
    }

    private fun parseSuggestions(response: String): List<GeminiQuestSuggestion> {
        // Extract JSON array from response (may be wrapped in markdown code block)
        val jsonStr = response
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val arr = JSONArray(jsonStr)
        val suggestions = mutableListOf<GeminiQuestSuggestion>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            suggestions.add(GeminiQuestSuggestion(
                type = obj.getString("type"),
                title = obj.getString("title"),
                targetSteps = obj.optLong("target_steps", 0),
                targetMinutes = obj.optLong("target_minutes", 0),
                targetApp = obj.optString("target_app", ""),
                rewardMinutes = obj.getLong("reward_minutes"),
                reason = obj.optString("reason", "")
            ))
        }
        return suggestions
    }
}

data class GeminiQuestSuggestion(
    val type: String,
    val title: String,
    val targetSteps: Long,
    val targetMinutes: Long,
    val targetApp: String,
    val rewardMinutes: Long,
    val reason: String
)
