package com.usman.musicplayer.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumColorCacheTest {

    @Test
    fun testAlbumColorCacheStoreAndRetrieve() {
        val palette = AlbumPalette(
            darkBackground = Color(0xFF0D0F1E),
            primary = Color(0xFF6366F1),
            secondary = Color(0xFF00E5FF),
            tertiary = Color(0xFF1E1B4B),
            accent = Color(0xFF38BDF8),
            surfaceGlass = Color(0x226366F1)
        )

        val uri = "https://example.com/artwork.jpg"
        AlbumColorCache.put(uri, palette)

        val retrieved = AlbumColorCache.get(uri)
        assertNotNull(retrieved)
        assertEquals(palette.darkBackground, retrieved?.darkBackground)
        assertEquals(palette.primary, retrieved?.primary)
        assertEquals(palette.secondary, retrieved?.secondary)
        assertEquals(palette.accent, retrieved?.accent)
    }
}
