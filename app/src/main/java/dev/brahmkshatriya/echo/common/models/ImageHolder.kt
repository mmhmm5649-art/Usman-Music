package dev.brahmkshatriya.echo.common.models

import kotlinx.serialization.Serializable

@Serializable
sealed class ImageHolder {
    @Serializable
    data class Network(val url: String) : ImageHolder()
    
    @Serializable
    data class Resource(val uri: String) : ImageHolder()

    @Serializable
    data class ByteArray(val bytes: kotlin.ByteArray) : ImageHolder()
}

fun String.toImageHolder(): ImageHolder = ImageHolder.Network(this)
