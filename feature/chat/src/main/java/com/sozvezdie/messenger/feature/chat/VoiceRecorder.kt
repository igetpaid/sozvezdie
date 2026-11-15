package com.sozvezdie.messenger.feature.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Готовое голосовое: файл и его длительность. */
data class VoiceRecording(
    val file: File,
    val durationMs: Long,
)

/**
 * Запись голосового сообщения.
 *
 * Пишем в M4A (AAC): такой файл хорошо сжимается и открывается
 * на любом телефоне. Максимальная длина 5 минут, проверяет экран.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L

    val isRecording: Boolean
        get() = recorder != null

    /** Сколько миллисекунд уже идёт запись. */
    fun elapsedMs(): Long =
        if (startedAt == 0L) 0L else System.currentTimeMillis() - startedAt

    /** Начинает запись. Возвращает ошибку, если микрофон занят. */
    fun start(): Result<Unit> = runCatching {
        if (recorder != null) return@runCatching

        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(128_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        startedAt = System.currentTimeMillis()
    }

    /**
     * Останавливает запись и отдаёт файл.
     * Если запись была слишком короткой, файл удаляем.
     */
    fun stop(): VoiceRecording? {
        val mediaRecorder = recorder ?: return null
        val duration = elapsedMs()
        val file = outputFile

        recorder = null
        startedAt = 0L
        outputFile = null

        val stopped = runCatching {
            mediaRecorder.stop()
        }
        mediaRecorder.release()

        if (stopped.isFailure || file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            return null
        }
        if (duration < MIN_DURATION_MS) {
            file.delete()
            return null
        }
        return VoiceRecording(file = file, durationMs = duration)
    }

    /** Отменяет запись и удаляет файл. */
    fun cancel() {
        val mediaRecorder = recorder ?: return
        recorder = null
        startedAt = 0L
        runCatching { mediaRecorder.stop() }
        mediaRecorder.release()
        outputFile?.delete()
        outputFile = null
    }

    companion object {
        const val MAX_DURATION_MS = 5 * 60 * 1000L
        const val MIN_DURATION_MS = 1_000L
    }
}
