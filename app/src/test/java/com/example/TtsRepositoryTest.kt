package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.tts.TtsEngineType
import com.example.data.tts.TtsRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TtsRepositoryTest {

    @Test
    fun speechRateAndPitch_clampedToValidRange() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = TtsRepositoryImpl(context, UserPreferencesRepository(context))

        repository.setSpeechRate(0.1f)
        assertEquals(0.5f, repository.speechRate.value, 0.01f)

        repository.setSpeechRate(3.0f)
        assertEquals(2.0f, repository.speechRate.value, 0.01f)

        repository.setSpeechPitch(0.2f)
        assertEquals(0.5f, repository.speechPitch.value, 0.01f)

        repository.setSpeechPitch(2.5f)
        assertEquals(2.0f, repository.speechPitch.value, 0.01f)
    }

    @Test
    fun engineTypeSelection_updatesState() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = TtsRepositoryImpl(context, UserPreferencesRepository(context))

        repository.setEngineType(TtsEngineType.SYSTEM)
        assertEquals(TtsEngineType.SYSTEM, repository.selectedEngineType.value)

        repository.setEngineType(TtsEngineType.SHERPA_ONNX_NEURAL)
        assertEquals(TtsEngineType.SHERPA_ONNX_NEURAL, repository.selectedEngineType.value)
    }

    @Test
    fun stop_resetsPlaybackState() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = TtsRepositoryImpl(context, UserPreferencesRepository(context))

        repository.stop()
        assertFalse("Playback state must be stopped after calling stop()", repository.playbackState.value.isPlaying)
    }
}
