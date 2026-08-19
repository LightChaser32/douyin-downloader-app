package com.douyin.crawler

import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Downloader {

    interface Callback {
        fun onProgress(percent: Int, done: Long, total: Long)
        fun onSuccess(file: File)
        fun onError(message: String)
    }

    private val executor: ExecutorService = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    private val deviceUA: String by lazy {
        "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }

    @Volatile
    private var cancelled = false

    fun cancelAll() {
        cancelled = true
    }

    fun resetCancel() {
        cancelled = false
    }

    fun toNoWatermark(url: String): String =
        url.replace("playwm", "play").replace("&watermark=1", "")

    fun download(url: String, target: File, callback: Callback) {
        downloadWithRetry(url, target, callback, 0)
    }

    private fun downloadWithRetry(url: String, target: File, callback: Callback, retryCount: Int) {
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val clean = toNoWatermark(url)
                connection = (URL(clean).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 60000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", deviceUA)
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Referer", "https://www.douyin.com/")
                    instanceFollowRedirects = true
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    post { callback.onError("HTTP $code") }
                    return@execute
                }
                val total = connection.contentLengthLong
                target.parentFile?.mkdirs()
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        while (true) {
                            if (cancelled) {
                                output.close()
                                input.close()
                                target.delete()
                                return@execute
                            }
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (done - lastReport >= 256 * 1024 || done == total) {
                                lastReport = done
                                val p = if (total > 0) ((done * 100) / total).toInt() else -1
                                post { callback.onProgress(p, done, total) }
                            }
                        }
                    }
                }
                post { callback.onSuccess(target) }
            } catch (e: Exception) {
                post { callback.onError(e.message ?: "下载失败") }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun post(r: () -> Unit) {
        main.post { r() }
    }
}