package com.douyin.crawler

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL

class WebViewLoader(
    context: Context,
    private val webView: WebView
) {
    companion object {
        private const val TAG = "DouyinLoader"
        private const val MAX_POLL = 12
    }

    interface Listener {
        fun onParsed(rawJson: String?)
        fun onState(text: String)
        fun onLoginRequired()
    }

    var listener: Listener? = null
    private var pending = false
    private var pollTask: Runnable? = null
    @Volatile
    private var targetAwemeId: String = ""
    private val failedDetail = java.util.Collections.synchronizedSet(HashSet<String>())
    private var desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    fun init() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // 关键：使用桌面 UA，移动 UA 会拿到"下载App"推广页而非含数据的页面
            userAgentString = desktopUA
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "onPageFinished url=$url pending=$pending")
                if (!pending) return
                startPolling()
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val urlStr = request?.url?.toString() ?: return null
                if (!isDataApi(urlStr)) return null
                Log.d(TAG, "intercept: ${urlStr.take(120)}")
                // 主线程取好 UA/Cookie/Header，后台线程只做纯网络
                val ua = desktopUA
                val cookie = CookieManager.getInstance().getCookie(urlStr) ?: ""
                val headers = HashMap<String, String>()
                request.requestHeaders.forEach { (name, value) ->
                    if (!name.equals("cookie", true) && !name.equals("host", true)) {
                        headers[name] = value
                    }
                }
                val isDetail = urlStr.contains("aweme/detail") || urlStr.contains("aweme/iteminfo")
                if (failedDetail.contains(urlStr)) {
                    Log.d(TAG, "detail already failed, pass through")
                    return null
                }
                if (!isDetail) {
                    // 非 detail 接口只旁路解析，不影响页面加载
                    Thread {
                        try {
                            val body = fetchBody(urlStr, ua, cookie, headers)
                            if (body != null) tryParseData(body)
                        } catch (e: Exception) {
                            Log.d(TAG, "fetch fail: ${e.message}")
                        }
                    }.start()
                    return null
                }
                // detail 接口：拦截并转发响应喂回 WebView，避免重复请求触发风控
                var response: WebResourceResponse? = null
                val done = java.util.concurrent.CountDownLatch(1)
                Thread {
                    try {
                        val body = fetchBody(urlStr, ua, cookie, headers)
                        if (body != null) {
                            tryParseData(body)
                            response = WebResourceResponse(
                                "application/json",
                                "utf-8",
                                java.io.ByteArrayInputStream(body.toByteArray())
                            )
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "detail fetch fail: ${e.message}")
                    } finally {
                        done.countDown()
                    }
                }.start()
                try {
                    if (done.await(15, java.util.concurrent.TimeUnit.SECONDS)) {
                        if (response == null) failedDetail.add(urlStr)
                        return response
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                failedDetail.add(urlStr)
                return null
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                val isMain = request?.isForMainFrame == true
                Log.d(TAG, "onReceivedError main=$isMain url=${request?.url} desc=${error?.description}")
                if (isMain) {
                    listener?.onState("页面加载异常: ${error?.description}")
                }
            }
        }
    }

    fun loadShareUrl(url: String) {
        pending = true
        stopPolling()
        webView.loadUrl(url)
    }

    fun setTargetAwemeId(id: String) {
        targetAwemeId = id
    }

    fun stop() {
        pending = false
        stopPolling()
        webView.stopLoading()
    }

    private fun isDataApi(u: String): Boolean {
        return u.contains("aweme/detail") ||
            u.contains("aweme/iteminfo") ||
            u.contains("/aweme/v1/web/")
    }

    private fun fetchBody(
        urlStr: String,
        ua: String,
        cookie: String,
        headers: Map<String, String>
    ): String? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", ua)
            conn.setRequestProperty("Accept", "*/*")
            if (cookie.isNotEmpty()) conn.setRequestProperty("Cookie", cookie)
            headers.forEach { (name, value) ->
                conn.setRequestProperty(name, value)
            }
            if (conn.getRequestProperty("Referer").isNullOrEmpty()) {
                conn.setRequestProperty("Referer", "https://www.douyin.com/")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.d(TAG, "api http $code for ${urlStr.take(100)}")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "api 200 len=${body.length} head=${body.take(220)}")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun tryParseData(body: String) {
        val root = try { JSONObject(body) } catch (e: Exception) {
            Log.d(TAG, "tryParse not-json: ${body.take(200)}")
            return
        }
        Log.d(TAG, "tryParse keys=${root.keys().asSequence().toList().toString()} status=${root.opt("status_code")}")
        val aweme: JSONObject? = when {
            root.has("aweme_detail") -> root.optJSONObject("aweme_detail")
            root.has("item_list") -> {
                val arr = root.optJSONArray("item_list")
                if (arr != null && arr.length() > 0) arr.optJSONObject(0) else null
            }
            else -> {
                val match = findAwemeById(root, targetAwemeId)
                if (match != null) match else findAweme(root, 0)
            }
        }
        if (aweme == null) return
        if (targetAwemeId.isNotEmpty() &&
            aweme.optString("aweme_id", "") != targetAwemeId
        ) {
            Log.d(TAG, "skip non-target aweme ${aweme.optString("aweme_id")}")
            return
        }
        val formatted = formatAweme(aweme)
        Log.d(TAG, "intercept parsed OK: ${formatted.take(300)}")
        synchronized(this) {
            if (!pending) return
            pending = false
        }
        stopPolling()
        webView.post { listener?.onParsed(formatted) }
    }

    private fun findAwemeById(o: Any?, targetId: String): JSONObject? {
        if (targetId.isEmpty() || o == null) return null
        return search(o, 0)
    }

    private fun search(o: Any?, depth: Int): JSONObject? {
        if (depth > 8 || o == null) return null
        if (o is JSONObject) {
            val id = o.optString("aweme_id", "")
            if (id == targetAwemeId && isFullAweme(o)) return o
            val it = o.keys()
            while (it.hasNext()) {
                val r = search(o.opt(it.next()), depth + 1)
                if (r != null) return r
            }
        } else if (o is JSONArray) {
            for (i in 0 until o.length()) {
                val r = search(o.opt(i), depth + 1)
                if (r != null) return r
            }
        }
        return null
    }

    private fun isFullAweme(o: JSONObject): Boolean {
        return o.has("video") || o.has("images") ||
            (o.has("aweme_id") && (o.has("desc") || o.has("author") || o.has("statistics")))
    }

    private fun findAweme(o: Any?, depth: Int): JSONObject? {
        if (depth > 8 || o == null) return null
        if (o is JSONObject) {
            if (o.has("aweme_id") || o.has("awemeId")) return o
            val it = o.keys()
            while (it.hasNext()) {
                val r = findAweme(o.opt(it.next()), depth + 1)
                if (r != null) return r
            }
        } else if (o is JSONArray) {
            for (i in 0 until o.length()) {
                val r = findAweme(o.opt(i), depth + 1)
                if (r != null) return r
            }
        }
        return null
    }

    private fun formatAweme(a: JSONObject): String {
        fun strList(arr: JSONArray?): List<String> {
            val out = mutableListOf<String>()
            if (arr != null) for (i in 0 until arr.length()) {
                val v = arr.opt(i)
                if (v is String) out.add(v)
            }
            return out
        }

        fun urlList(obj: JSONObject?, key: String): List<String> {
            obj ?: return emptyList()
            val o = obj.optJSONObject(key) ?: return emptyList()
            return strList(o.optJSONArray("url_list"))
        }

        val video = a.optJSONObject("video")
        val videoUrls = urlList(video, "play_addr").toMutableList()
        if (videoUrls.isEmpty() && video != null) {
            val uri = video.optJSONObject("play_addr")?.optString("uri", "") ?: ""
            if (uri.isNotEmpty()) {
                videoUrls.add("https://aweme.snssdk.com/aweme/v1/play/?video_id=$uri&ratio=1080p&line=0")
            }
        }

        val coverUrls = mutableListOf<String>()
        if (video != null) {
            for (k in listOf("cover", "origin_cover", "dynamic_cover")) {
                coverUrls.addAll(strList(video.optJSONObject(k)?.optJSONArray("url_list")))
            }
        }

        val imageUrls = mutableListOf<String>()
        val images = a.optJSONArray("images")
        if (images != null) for (i in 0 until images.length()) {
            val im = images.optJSONObject(i)
            val u = im?.optJSONArray("url_list")?.optString(0, "") ?: ""
            if (u.isNotEmpty()) imageUrls.add(u)
        }

        val author = a.optJSONObject("author") ?: JSONObject()
        val stats = a.optJSONObject("statistics") ?: JSONObject()

        return JSONObject().apply {
            put("ok", true)
            put("url", "")
            put("awemeId", a.optString("aweme_id", ""))
            put("desc", a.optString("desc", ""))
            put("authorName", author.optString("nickname", ""))
            put("authorUniqueId", author.optString("unique_id", ""))
            put("videoUrlList", videoUrls)
            put("coverUrlList", coverUrls)
            put("images", imageUrls)
            put("digg", stats.optLong("digg_count", 0))
            put("comment", stats.optLong("comment_count", 0))
            put("share", stats.optLong("share_count", 0))
        }.toString()
    }

    private fun startPolling() {
        stopPolling()
        if (!pending) return
        val js = readAsset()
        val task = object : Runnable {
            var count = 0
            override fun run() {
                if (!pending) return
                count++
                webView.evaluateJavascript(js) { result ->
                    if (!pending) return@evaluateJavascript
                    val clean = unquote(result)
                    Log.d(TAG, "poll#$count inject=$clean")
                    val ok = try {
                        JSONObject(clean ?: "").optBoolean("ok", false)
                    } catch (e: Exception) {
                        false
                    }
                    if (ok) {
                        pending = false
                        listener?.onParsed(clean)
                    } else if (count < MAX_POLL && pollTask === this) {
                        webView.postDelayed(this, 1500)
                    } else if (pending) {
                        pending = false
                        listener?.onParsed(null)
                    }
                }
            }
        }
        pollTask = task
        webView.post(task)
    }

    private fun stopPolling() {
        pollTask?.let { webView.removeCallbacks(it) }
        pollTask = null
    }

    private fun unquote(s: String?): String? {
        if (s == null) return null
        return try {
            val v = JSONTokener(s).nextValue()
            if (v is String) v else s
        } catch (e: Exception) {
            s
        }
    }

    private fun readAsset(): String {
        return try {
            val input = webView.context.assets.open("extract.js")
            input.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "null"
        }
    }
}