package com.timebet.app.core.auth

import android.content.Context
import android.os.Build
import android.util.Log
import com.timebet.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles email/password authentication via Supabase Auth REST API.
 *
 * Flow:
 * 1. User enters email + password
 * 2. Sign up (if new) or sign in (if returning)
 * 3. Supabase returns access_token + refresh_token + user_id
 * 4. Store session locally
 * 5. All SyncEngine requests use the access_token
 */
class AuthManager(private val context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val PREF_NAME = "timebet_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            id
        }
    }

    val deviceIdVal: String get() = deviceId

    val deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null) ?: run {
            val autoName = Build.MODEL ?: "Android Device"
            prefs.edit().putString(KEY_DEVICE_NAME, autoName).apply()
            autoName
        }

    fun setDeviceName(name: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        val token = getAccessToken()
        if (token != null) {
            _authState.value = AuthState.Authenticated(
                userId = getUserId() ?: "",
                email = getEmail() ?: "",
                displayName = getEmail()
            )
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /**
     * Sign up a new user with email + password.
     */
    suspend fun signUp(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            if (password.length < 6) return@withContext AuthResult.Error("Password must be at least 6 characters")

            val url = URL("${BuildConfig.SUPABASE_URL}/auth/v1/signup")
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            val response = post(url, body)
            if (response == null) return@withContext AuthResult.Error("Sign up failed. Check your connection.")

            storeSession(response, email)
            _authState.value = AuthState.Authenticated(
                userId = getUserId() ?: "",
                email = email,
                displayName = email
            )
            AuthResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Sign up failed", e)
            AuthResult.Error(e.message ?: "Sign up failed")
        }
    }

    /**
     * Sign in an existing user with email + password.
     */
    suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=password")
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            val response = post(url, body)
            if (response == null) return@withContext AuthResult.Error("Invalid email or password")

            storeSession(response, email)
            _authState.value = AuthState.Authenticated(
                userId = getUserId() ?: "",
                email = email,
                displayName = email
            )
            AuthResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            AuthResult.Error("Invalid email or password")
        }
    }

    /**
     * Refresh the Supabase session using the refresh token.
     */
    suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext false
        try {
            val url = URL("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
            val body = JSONObject().apply { put("refresh_token", refreshToken) }
            val response = post(url, body) ?: return@withContext false

            val json = JSONObject(response)
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, json.getString("access_token"))
                .putString(KEY_REFRESH_TOKEN, json.optString("refresh_token", refreshToken))
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Refresh failed", e)
            false
        }
    }

    fun signOut() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .apply()
        _authState.value = AuthState.Unauthenticated
    }

    // ─── Private helpers ───

    private fun storeSession(responseBody: String, email: String) {
        val json = JSONObject(responseBody)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, json.getString("access_token"))
            .putString(KEY_REFRESH_TOKEN, json.optString("refresh_token", ""))
            .putString(KEY_USER_ID, json.getJSONObject("user").getString("id"))
            .putString(KEY_EMAIL, email)
            .apply()
    }

    private fun post(url: URL, jsonBody: JSONObject): String? {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(jsonBody.toString())
        writer.flush()
        writer.close()

        val code = conn.responseCode
        if (code !in 200..299) {
            val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { "" }
            Log.e(TAG, "Auth request failed: HTTP $code — $errorBody")
            conn.disconnect()
            return null
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val body = reader.readText()
        reader.close()
        conn.disconnect()
        return body
    }
}

sealed class AuthState {
    data object Loading : AuthState()
    data object Unauthenticated : AuthState()
    data class Authenticated(
        val userId: String,
        val email: String,
        val displayName: String?
    ) : AuthState()
}

sealed class AuthResult {
    data object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}
