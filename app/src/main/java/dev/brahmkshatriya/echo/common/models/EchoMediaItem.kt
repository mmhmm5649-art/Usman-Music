package dev.brahmkshatriya.echo.common.models

import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
sealed interface EchoMediaItem {
    val id: String
    val title: String
    val cover: ImageHolder?
    val description: String?
    val background: ImageHolder?
    val subtitle: String?
    val extras: Map<String, String>
    val isRadioSupported: Boolean
    val isFollowable: Boolean
    val isSaveable: Boolean
    val isLikeable: Boolean
    val isHideable: Boolean
    val isShareable: Boolean
    val isExplicit: Boolean
}

@Serializable
sealed interface Shelf {
    val id: String
    val title: String
    val extras: Map<String, String>

    @Serializable
    sealed interface Lists<T : Any> : Shelf {
        val list: List<T>
        
        @Serializable
        data class Items(
            override val id: String,
            override val title: String,
            override val list: List<EchoMediaItem>,
            override val extras: Map<String, String> = emptyMap()
        ) : Lists<EchoMediaItem>

        @Serializable
        data class Tracks(
            override val id: String,
            override val title: String,
            override val list: List<Track>,
            override val extras: Map<String, String> = emptyMap()
        ) : Lists<Track>
    }

    @Serializable
    data class Item(
        val media: EchoMediaItem
    ) : Shelf {
        override val id = media.id
        override val title = media.title
        override val extras = media.extras
    }
}

@Serializable
data class Feed<T>(
    val tabs: List<Tab> = emptyList(),
    val loader: (suspend (Tab?) -> Data<T>)? = null
) {
    @Serializable
    data class Tab(val id: String, val title: String)
    
    @Serializable
    data class Data<T>(
        val pagedData: dev.brahmkshatriya.echo.common.helpers.PagedData<T>,
        val buttons: Buttons? = null,
        val background: ImageHolder? = null
    )
    
    @Serializable
    data class Buttons(
        val showSearch: Boolean = false,
        val showSort: Boolean = false,
        val showPlayAndShuffle: Boolean = false
    )
}
