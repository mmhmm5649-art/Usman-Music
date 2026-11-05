package com.usman.musicplayer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.usman.musicplayer.viewmodel.MusicPlayerViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ViewModelInitTest {

    @Test
    fun viewModel_initializesWithoutCrashing() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MusicPlayerViewModel(app)
        assert(viewModel != null)
    }
}
