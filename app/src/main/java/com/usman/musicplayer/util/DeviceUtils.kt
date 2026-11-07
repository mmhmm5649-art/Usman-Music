package com.usman.musicplayer.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceUtils {

    /**
     * Determines whether the current device is an Android TV / Google TV.
     * Uses official Google recommendations:
     * 1. Checks UiModeManager for UI_MODE_TYPE_TELEVISION
     * 2. Checks resources configuration uiMode
     * 3. Checks PackageManager for FEATURE_LEANBACK and FEATURE_TELEVISION
     */
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }

        val configUiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (configUiMode == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }

        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        ) {
            return true
        }

        return false
    }
}
