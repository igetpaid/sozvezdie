package com.sozvezdie.messenger.feature.chat

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Один плеер на всё приложение.
 *
 * Так два голосовых не могут играть одновременно: при запуске
 * нового предыдущее останавливается.
 */
object VoicePlayback {

    private var player: MediaPlayer? = null

    private val _playingUrl = MutableStateFlow<String?>(null)
    val playingUrl: StateFlow<String?> = _playingUrl.asStateFlow()

    private val _loadingUrl = MutableStateFlow<String?>(null)
    val loadingUrl: StateFlow<String?> = _loadingUrl.asStateFlow()

    /** Включает запись или ставит её на паузу. */
    fun toggle(url: String) {
        if (url.isBlank()) return
        val current = player
        if (_playingUrl.value == url && current != null) {
            if (current.isPlaying) {
                current.pause()
                _playingUrl.value = null
            } else {
                current.start()
                _playingUrl.value = url
            }
            return
        }

        stop()
        _loadingUrl.value = url
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build(),
        )
        mediaPlayer.setDataSource(url)
        mediaPlayer.setOnPreparedListener {
            if (player !== mediaPlayer) return@setOnPreparedListener
            _loadingUrl.value = null
            _playingUrl.value = url
            it.start()
        }
        mediaPlayer.setOnCompletionListener {
            _playingUrl.value = null
            stop()
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            _playingUrl.value = null
            _loadingUrl.value = null
            stop()
            true
        }
        mediaPlayer.prepareAsync()
    }

    fun stop() {
        player?.let { mediaPlayer ->
            runCatching { mediaPlayer.stop() }
            mediaPlayer.release()
        }
        player = null
        _playingUrl.value = null
        _loadingUrl.value = null
    }
}
