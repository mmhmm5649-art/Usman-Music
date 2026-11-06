package dev.brahmkshatriya.echo.common.settings

interface Settings {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    
    fun getStringSet(key: String): Set<String>?
    fun putStringSet(key: String, value: Set<String>?)
    
    fun getInt(key: String): Int?
    fun putInt(key: String, value: Int?)
    
    fun getBoolean(key: String): Boolean?
    fun putBoolean(key: String, value: Boolean?)
}

sealed interface Setting {
    val title: String
    val key: String
}

data class SettingCategory(
    override val title: String,
    val description: String? = null
) : Setting {
    override val key: String = ""
}

data class SettingSwitch(
    override val key: String,
    override val title: String,
    val description: String? = null,
    val defaultValue: Boolean = false
) : Setting

data class SettingTextInput(
    override val key: String,
    override val title: String,
    val description: String? = null,
    val defaultValue: String = ""
) : Setting

data class SettingList(
    override val key: String,
    override val title: String,
    val description: String? = null,
    val entries: List<String> = emptyList(),
    val defaultValue: Int = 0
) : Setting

data class SettingSlider(
    override val key: String,
    override val title: String,
    val description: String? = null,
    val defaultValue: Int = 0,
    val min: Int = 0,
    val max: Int = 100
) : Setting

data class SettingMultipleChoice(
    override val key: String,
    override val title: String,
    val description: String? = null,
    val entries: List<String> = emptyList(),
    val defaultValues: Set<Int> = emptySet()
) : Setting

data class SettingOnClick(
    override val key: String,
    override val title: String,
    val description: String? = null
) : Setting
