package dev.brahmkshatriya.echo.common.providers

import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings

interface SettingsProvider {
    suspend fun getSettingItems(): List<Setting>
    fun setSettings(settings: Settings)
}
