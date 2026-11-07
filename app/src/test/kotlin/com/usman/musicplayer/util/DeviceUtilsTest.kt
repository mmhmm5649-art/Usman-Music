package com.usman.musicplayer.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DeviceUtilsTest {

    @Test
    fun testPhoneModeReturnsFalse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val isTv = DeviceUtils.isTvDevice(context)
        // Robolectric standard phone defaults to false
        assertFalse(isTv)
    }

    @Test
    @Config(qualifiers = "television")
    fun testTelevisionConfigurationReturnsTrue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val isTv = DeviceUtils.isTvDevice(context)
        assertTrue(isTv)
    }

    @Test
    fun testLeanbackFeatureReturnsTrue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowPackageManager = shadowOf(context.packageManager)
        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_LEANBACK, true)

        val isTv = DeviceUtils.isTvDevice(context)
        assertTrue(isTv)
    }
}
