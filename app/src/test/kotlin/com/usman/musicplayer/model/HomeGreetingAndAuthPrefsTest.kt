package com.usman.musicplayer.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.usman.musicplayer.data.repository.PersistedAuthMode
import com.usman.musicplayer.data.repository.UserProfilePreferencesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeGreetingAndAuthPrefsTest {

    @Test
    fun timeOfDayGreeting_coversAll24HoursWithoutNameOrEmoji() {
        for (hour in 5..11) {
            val greeting = HomeLanguagePolicy.timeOfDayGreeting(hour)
            assertEquals("Hour $hour should be Good morning", "Good morning", greeting)
        }
        for (hour in 12..16) {
            val greeting = HomeLanguagePolicy.timeOfDayGreeting(hour)
            assertEquals("Hour $hour should be Good afternoon", "Good afternoon", greeting)
        }
        for (hour in (17..23) + (0..4)) {
            val greeting = HomeLanguagePolicy.timeOfDayGreeting(hour)
            assertEquals("Hour $hour should be Good evening", "Good evening", greeting)
            assertFalse("Greeting should not include comma or name", greeting.contains(","))
        }
    }

    @Test
    fun userProfilePreferencesStore_persistsAndRestoresAuthModeAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstStore = UserProfilePreferencesStore(context)
        assertEquals(PersistedAuthMode.UNDECIDED, firstStore.getPersistedAuthMode())

        firstStore.setPersistedAuthMode(PersistedAuthMode.GUEST)
        val restartedStoreAfterGuest = UserProfilePreferencesStore(context)
        assertEquals(PersistedAuthMode.GUEST, restartedStoreAfterGuest.getPersistedAuthMode())

        restartedStoreAfterGuest.setPersistedAuthMode(PersistedAuthMode.GOOGLE)
        val restartedStoreAfterGoogle = UserProfilePreferencesStore(context)
        assertEquals(PersistedAuthMode.GOOGLE, restartedStoreAfterGoogle.getPersistedAuthMode())

        restartedStoreAfterGoogle.clearAuthMode()
        val restartedStoreAfterSignOut = UserProfilePreferencesStore(context)
        assertEquals(PersistedAuthMode.UNDECIDED, restartedStoreAfterSignOut.getPersistedAuthMode())
    }
}
