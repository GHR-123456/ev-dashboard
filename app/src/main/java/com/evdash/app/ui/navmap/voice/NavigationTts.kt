package com.evdash.app.ui.navmap.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * P3 占位:把 Android TTS 接到一个 [speak] 入口。
 *
 * 实际接路径时:
 * - 路径规划下达 maneuver → 调 [speak]
 * - 车机用 `AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` 走导航专用音频通道
 *
 * 现在只暴露接口,等 P3 的路径引擎来调用。
 */
@Singleton
class NavigationTts @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var focusRequest: AudioFocusRequest? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            _isReady.value = status == TextToSpeech.SUCCESS
            if (_isReady.value) {
                tts?.language = Locale.SIMPLIFIED_CHINESE
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            } else {
                Log.w(TAG, "tts init failed: $status")
            }
        }
    }

    fun speak(text: String, queue: Boolean = false) {
        val engine = tts ?: return
        val mode = if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        engine.speak(text, mode, null, text.hashCode().toString())
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
    }

    @Suppress("unused")
    fun ducking(active: Boolean): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return if (active) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            val req = focusRequest ?: return true
            val result = am.abandonAudioFocusRequest(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusRequest = null
            result
        }
    }

    private companion object {
        const val TAG = "NavigationTts"
    }
}
