package dev.brahmkshatriya.echo.common.helpers

import kotlinx.serialization.Serializable

@Serializable
data class PagedData<T>(
    val items: List<T>,
    val continuation: String? = null
) {
    companion object {
        fun <T> Single(loader: suspend () -> List<T>) = PagedData<T>(emptyList())
    }
}
