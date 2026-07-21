package com.timebet.app.features.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore by preferencesDataStore(name = "onboarding_prefs")

object OnboardingPreferences {

    private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    private const val STARTUP_PREFS = "timebet_startup"
    private const val KEY_COMPLETE = "onboarding_complete"

    /**
     * Synchronous check for Activity.onCreate. Uses SharedPreferences
     * as a fast cache of the DataStore value. DataStore is the source of truth.
     */
    fun hasCompletedOnboarding(context: Context): Boolean {
        return context.getSharedPreferences(STARTUP_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETE, false)
    }

    suspend fun setOnboardingComplete(context: Context) {
        // Write to DataStore (source of truth)
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETE] = true
        }
        // Mirror to SharedPreferences for synchronous reads
        context.getSharedPreferences(STARTUP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETE, true)
            .apply()
    }
}
