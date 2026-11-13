package com.usman.musicplayer.data.repository

import android.content.Context
import android.content.SharedPreferences

enum class PersistedAuthMode {
    UNDECIDED,
    GOOGLE,
    GUEST
}

/**
 * Persists the user's authentication choice across app restarts:
 * - UNDECIDED: First launch or explicitly signed out -> show Login screen
 * - GOOGLE: User previously signed in with Google -> auto-restore Firebase session
 * - GUEST: User chose Continue as Guest -> remember guest session across restarts
 */
class UserProfilePreferencesStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun getPersistedAuthMode(): PersistedAuthMode {
        val raw = prefs.getString(KEY_AUTH_MODE, null)
        return PersistedAuthMode.entries.firstOrNull { it.name == raw } ?: PersistedAuthMode.UNDECIDED
    }

    fun setPersistedAuthMode(mode: PersistedAuthMode) {
        prefs.edit().putString(KEY_AUTH_MODE, mode.name).apply()
    }

    fun clearAuthMode() {
        prefs.edit().putString(KEY_AUTH_MODE, PersistedAuthMode.UNDECIDED.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "usman_auth_session_prefs"
        private const val KEY_AUTH_MODE = "persisted_auth_mode"
    }
}
