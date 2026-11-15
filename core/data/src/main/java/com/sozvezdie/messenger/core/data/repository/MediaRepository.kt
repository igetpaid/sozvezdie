package com.sozvezdie.messenger.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.sozvezdie.messenger.core.data.remote.SupabaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Результат загрузки файла: ссылка, имя и размер. */
data class UploadedFile(
    val url: String,
    val fileName: String,
    val size: Long,
)

/**
 * Фото, документы, голосовые и аватары.
 *
 * Файлы уходят в Supabase Storage, а в Firebase сохраняется только ссылка.
 * Перед загрузкой фотографии сжимаются, чтобы не тратить мобильный интернет.
 */
class MediaRepository(
    private val context: Context,
    private val storage: SupabaseStorage,
) {

    private val contentResolver get() = context.contentResolver

    suspend fun uploadImage(chatId: String, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val mime = contentResolver.getType(uri) ?: "image/jpeg"
                val raw = readBytes(uri) ?: error("Не удалось прочитать выбранное фото")
                val bytes = compressImage(raw, mime, maxSide = 1600)
                val path = "images/$chatId/${UUID.randomUUID()}.jpg"
                storage.upload(path, bytes, "image/jpeg").getOrThrow()
            }.mapError("Не удалось загрузить фото")
        }

    suspend fun uploadFile(chatId: String, uri: Uri): Result<UploadedFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = queryName(uri)
                val size = querySize(uri)
                if (size > MAX_FILE_BYTES) {
                    error("Файл больше 25 МБ")
                }

                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = readBytes(uri) ?: error("Не удалось прочитать выбранный файл")
                if (bytes.size > MAX_FILE_BYTES) {
                    error("Файл больше 25 МБ")
                }

                val extension = extensionFor(mime, fileName)
                val path = "files/$chatId/${UUID.randomUUID()}.$extension"
                val url = storage.upload(path, bytes, mime).getOrThrow()
                UploadedFile(url = url, fileName = fileName, size = bytes.size.toLong())
            }.mapError("Не удалось загрузить файл")
        }

    suspend fun uploadVoice(chatId: String, file: File): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists() || file.length() == 0L) error("Запись не сохранилась")
                val path = "voice/$chatId/${UUID.randomUUID()}.m4a"
                storage.upload(path, file.readBytes(), "audio/mp4").getOrThrow()
            }.mapError("Не удалось отправить голосовое")
        }

    suspend fun uploadAvatar(uid: String, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = readBytes(uri) ?: error("Не удалось прочитать фото")
                val bytes = compressSquare(raw, size = 512)
                val path = "avatars/$uid/${UUID.randomUUID()}.jpg"
                storage.upload(path, bytes, "image/jpeg").getOrThrow()
            }.mapError("Не удалось загрузить аватар")
        }

    suspend fun uploadGroupPhoto(chatId: String, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = readBytes(uri) ?: error("Не удалось прочитать фото")
                val bytes = compressSquare(raw, size = 512)
                val path = "groups/$chatId/${UUID.randomUUID()}.jpg"
                storage.upload(path, bytes, "image/jpeg").getOrThrow()
            }.mapError("Не удалось загрузить фото группы")
        }

    /** Скачивает файл из Supabase, чтобы открыть его другим приложением. */
    suspend fun downloadFile(url: String, fileName: String): Result<File> =
        storage.download(url, fileName)

    /** Копирует выбранное фото в память приложения: обои не пропадут после перезапуска. */
    suspend fun saveWallpaper(chatId: String, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = readBytes(uri) ?: error("Не удалось прочитать фото")
                val bytes = compressImage(raw, "image/jpeg", maxSide = 1400)
                val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                val file = File(dir, "$chatId.jpg")
                file.writeBytes(bytes)
                file.absolutePath
            }.mapError("Не удалось сохранить обои")
        }

    private fun compressImage(raw: ByteArray, mime: String, maxSide: Int): ByteArray {
        // GIF может быть анимированным: его не пережимаем.
        if (mime == "image/gif") return raw

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return raw

        // inSampleSize уменьшает картинку при чтении, поэтому большое фото
        // не занимает всю память телефона.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            raw,
            0,
            raw.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return raw

        val scaled = scaleDown(decoded, maxSide)
        var result = toJpeg(scaled, quality = 85)
        if (result.size > MAX_IMAGE_BYTES) {
            result = toJpeg(scaled, quality = 70)
        }
        return result
    }

    private fun compressSquare(raw: ByteArray, size: Int): ByteArray {
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: error("Не удалось обработать фото")
        val side = minOf(decoded.width, decoded.height)
        val left = (decoded.width - side) / 2
        val top = (decoded.height - side) / 2
        val square = Bitmap.createBitmap(decoded, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        return toJpeg(scaled, quality = 85)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun toJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun readBytes(uri: Uri): ByteArray? =
        contentResolver.openInputStream(uri)?.use { it.readBytes() }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return "Файл"
    }

    private fun querySize(uri: Uri): Long {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
                }
            }
        return 0L
    }

    private fun extensionFor(mime: String, fileName: String): String {
        val fromName = fileName.substringAfterLast('.', "").lowercase()
        if (fromName.isNotBlank() && fromName.length <= 5) return fromName
        return when {
            mime.contains("pdf") -> "pdf"
            mime.contains("word") -> "docx"
            mime.contains("excel") -> "xlsx"
            mime.contains("zip") -> "zip"
            mime.contains("text") -> "txt"
            mime.contains("audio") -> "m4a"
            else -> "bin"
        }
    }

    private fun <T> Result<T>.mapError(fallback: String): Result<T> {
        val error = exceptionOrNull() ?: return this
        return Result.failure(Exception(error.message ?: fallback))
    }

    private companion object {
        const val MAX_FILE_BYTES = 25 * 1024 * 1024L
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    }
}
