package com.timebet.app.core.auth

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
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
 * Handles Google Sign-In and Supabase Auth token exchange.
 *
 * Flow:
 * 1. User taps "Sign in with Google" → GoogleSignInClient.getSignInIntent()
 * 2. On result: extract Google ID token
 * 3. Exchange ID token with Supabase Auth REST API for Supabase session
 * 4. Store session (access_token, refresh_token, user_id) in DataStore
 * 5. All subsequent SyncEngine requests use the access_token
 */
class AuthManager(private val context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val PREF_NAME = "timebet_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
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

    /** Auto-detected device name from Build.MODEL, user can customize */
    val deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null)
            ?: run {
                val autoName = Build.MODEL ?: "Android Device"
                prefs.edit().putString(KEY_DEVICE_NAME, autoName).apply()
                autoName
            }

    fun setDeviceName(name: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    init {
        // Check for existing session on startup
        val token = getAccessToken()
        if (token != null) {
            _authState.value = AuthState.Authenticated(
                userId = getUserId() ?: "",
                email = getEmail() ?: "",
                displayName = getDisplayName()
            )
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    val deviceIdVal: String get() = deviceId

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getDisplayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)

    /**
     * Handle the result from Google Sign-In activity.
     * Extracts the ID token and exchanges it for a Supabase session.
     */
    suspend fun handleSignInResult(data: Intent?): AuthResult = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(Exception::class.java)
            val idToken = account?.idToken ?: return@withContext AuthResult.Error("No ID token received")

            // Exchange Google ID token for Supabase session
            val session = exchangeTokenWithSupabase(idToken)
            if (session == null) {
                return@withContext AuthResult.Error("Failed to exchange token with Supabase")
            }

            // Store session
            storeSession(session, account.email ?: "", account.displayName ?: "")

            _authState.value = AuthState.Authenticated(
                userId = session.userId,
                email = account.email ?: "",
                displayName = account.displayName
            )

            AuthResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            AuthResult.Error(e.message ?: "Sign-in failed")
        }
    }

    /**
     * Exchange a Google ID token for a Supabase access token.
     * POST to Supabase Auth endpoint: /auth/v1/token?grant_type=id_token
     */
    private suspend fun exchangeTokenWithSupabase(idToken: String): SupabaseSession? {
        try {
            val url = URL("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=id_token")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val body = JSONObject().apply {
                put("id_token", idToken)
                put("provider", "google")
                put("gotrue_meta_security", JSONObject().apply {
                    put("access_token", idToken)
                })
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code !in 200..299) {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { "unknown" }
                Log.e(TAG, "Supabase token exchange failed: HTTP $code, body: $errorBody")
                conn.disconnect()
                return null
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val responseBody = reader.readText()
            reader.close()
            conn.disconnect()

            val json = JSONObject(responseBody)
            return SupabaseSession(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token", ""),
                userId = json.getJSONObject("user").getString("id")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange error", e)
            return null
        }
    }

    /**
     * Refresh the Supabase session using the refresh token.
     */
    suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext false
        try {
            val url = URL("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val body = JSONObject().apply {
                put("refresh_token", refreshToken)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@withContext false
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val responseBody = reader.readText()
            reader.close()
            conn.disconnect()

            val json = JSONObject(responseBody)
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
        googleSignInClient.signOut()
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_DISPLAY_NAME)
            .apply()
        _authState.value = AuthState.Unauthenticated
    }

    private fun storeSession(session: SupabaseSession, email: String, displayName: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, email)
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()
    }

    private data class SupabaseSession(
        val accessToken: String,
        val refreshToken: String,
        val userId: String
    )
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
