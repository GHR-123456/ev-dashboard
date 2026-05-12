package com.evdash.app.data.map.remote

import com.evdash.app.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OkHttp 简包装,只暴露两件事:拉清单 + 带断点续传的下载。
 *
 * P0 没有任何调用方;P2 起被 `MapUpdateWorker` 与 Settings 页"立即更新"按钮使用。
 */
@Singleton
class MapUpdateApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    /** 拉清单。404 / 5xx / JSON 损坏统一抛 [IOException]。 */
    suspend fun fetchManifest(url: String = BuildConfig.MAP_MANIFEST_URL): MapManifestDto =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("manifest http ${resp.code}")
                val body = resp.body?.string() ?: throw IOException("manifest empty body")
                runCatching { json.decodeFromString(MapManifestDto.serializer(), body) }
                    .getOrElse { throw IOException("manifest parse: ${it.message}", it) }
            }
        }

    /**
     * 把 [packageUrl] 下载到 [destPart](.part 后缀),失败时保留 part 文件供下次续传。
     *
     * @param onProgress 回调签名为 `(bytesSoFar, totalBytes)`,worker 用它打通知进度。
     */
    suspend fun downloadPackage(
        packageUrl: String,
        destPart: File,
        onProgress: suspend (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        destPart.parentFile?.mkdirs()
        val existing = if (destPart.exists()) destPart.length() else 0L

        val reqBuilder = Request.Builder().url(packageUrl).get()
        if (existing > 0L) reqBuilder.header("Range", "bytes=$existing-")

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("download http ${resp.code}")
            val body = resp.body ?: throw IOException("download empty body")

            val appending = existing > 0L && resp.code == 206
            val total = resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: (existing + body.contentLength()).takeIf { it > 0L }
                ?: -1L

            body.byteStream().use { input ->
                FileOutputStream(destPart, appending).use { output ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var bytesSoFar = if (appending) existing else 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        bytesSoFar += n
                        onProgress(bytesSoFar, total)
                    }
                    output.flush()
                }
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
