package com.sozvezdie.messenger.core.data.remote

import android.content.Context
import com.sozvezdie.messenger.core.data.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Загрузка медиа в Supabase Storage.
 *
 * Firebase остаётся для входа и переписки, а фотографии, файлы и голосовые
 * лежат в публичном bucket chat-media. В сообщение записываем только ссылку.
 */
class SupabaseStorage(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val configured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    // Убираем лишний слэш в конце адреса, чтобы ссылки не получались с «//».
    private val baseUrl: String
        get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    /** Загружает байты и возвращает публичную ссылку. */
    suspend fun upload(path: String, bytes: ByteArray, mime: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (!configured) {
                return@withContext Result.failure(
                    Exception(
                        "Хранилище медиа не настроено. Проверьте SUPABASE_URL и " +
                            "SUPABASE_ANON_KEY в local.properties",
                    ),
                )
            }

            var lastError: Exception? = null
            // При плохой сети пробуем ещё раз: загрузка больших файлов часто срывается.
            repeat(2) { attempt ->
                val result = runCatching {
                    val request = Request.Builder()
                        .url("$baseUrl/storage/v1/object/$BUCKET/$path")
                        .put(bytes.toRequestBody(mime.toMediaTypeOrNull()))
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("Сервер не принял файл: ${response.code}")
                        }
                    }

                    "$baseUrl/storage/v1/object/public/$BUCKET/$path"
                }

                result.onSuccess { return@withContext Result.success(it) }
                lastError = result.exceptionOrNull() as? Exception
                if (attempt == 0) {
                    kotlinx.coroutines.delay(800)
                }
            }

            Result.failure(lastError ?: Exception("Не удалось загрузить файл"))
        }

    /** Скачивает файл из Supabase во временную папку приложения. */
    suspend fun download(url: String, fileName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (url.isBlank()) error("Файл недоступен")

                val request = Request.Builder().url(url).build()
                val bytes = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Не удалось скачать файл")
                    response.body?.bytes() ?: error("Пустой файл")
                }

                val dir = File(context.cacheDir, "media").apply { mkdirs() }
                val safeName = fileName.ifBlank { "file" }
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val file = File(dir, "${System.currentTimeMillis()}_$safeName")
                file.writeBytes(bytes)
                file
            }
        }

    private companion object {
        const val BUCKET = "chat-media"
    }
}
