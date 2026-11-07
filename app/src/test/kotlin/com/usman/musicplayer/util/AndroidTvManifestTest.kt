package com.usman.musicplayer.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidTvManifestTest {

    @Test
    fun testLeanbackLauncherIntentResolvesMainActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager

        val tvIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            setPackage(context.packageName)
        }

        val resolveInfos = pm.queryIntentActivities(tvIntent, 0)
        assertTrue("Manifest must include LEANBACK_LAUNCHER category for Android TV", resolveInfos.isNotEmpty())
        val activity = resolveInfos.firstOrNull()?.activityInfo
        assertNotNull(activity)
        assertTrue(activity?.name?.contains("MainActivity") == true)
    }

    @Test
    fun testPhoneLauncherIntentResolvesMainActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager

        val phoneIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(context.packageName)
        }

        val resolveInfos = pm.queryIntentActivities(phoneIntent, 0)
        assertTrue("Manifest must preserve standard LAUNCHER category for Android phones", resolveInfos.isNotEmpty())
    }
}
