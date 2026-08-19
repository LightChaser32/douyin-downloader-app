package com.douyin.crawler

import android.content.Context
import android.util.Log
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 抖音 detail 接口直连客户端。
 * 复刻 douyin-downloader 项目的方案：
 *   - 登录 Cookie + a_bogus 签名直连 /aweme/v1/web/aweme/detail/
 *   - 视频用 aid=1128，图文用 aid=6383（失败自动切换）
 */
class ApiClient(
    private val context: Context,
    private val signWebView: WebView
) {

    companion object {
        private const val TAG = "DouyinApi"

        // 与 Python 项目一致的桌面 UA
        private val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"

        private val AID_VIDEO = arrayOf("6383", "1128")
        private val AID_NOTE = arrayOf("6383", "1128")

        fun extractIdFromUrl(url: String): String? {
            val m = Regex("/(?:video|note|gallery|mix)/(\\d+)").find(url)
            if (m != null) return m.groupValues[1]
            return Regex("(?:share/video|share/note)/(\\d+)").find(url)?.groupValues?.get(1)
        }

        fun extractIdFromLocation(location: String): String? {
            val m = Regex("(?:share/video|share/note|video|note)/(\\d+)").find(location)
            return m?.groupValues?.get(1)
        }
    }

    /**
     * 解析分享链接，返回 aweme_id。
     * 完整链接直接提取；短链 302 跟随重定向拿 Location 再提取。
     */
    fun resolveAwemeId(url: String): String? {
        url.let {
            val id = extractIdFromUrl(it)
            if (id != null) return id
        }
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "*/*")
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if ((code == 301 || code == 302 || code == 303 || code == 307) && location != null) {
                extractIdFromLocation(location)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "resolve short link fail: ${e.message}")
            null
        }
    }

    /**
     * 直连 detail 接口获取作品数据，返回与旧 formatAweme 相同结构的 JSON 字符串。
     */
    fun fetchDetail(awemeId: String): String? {
        val aids = determineAids(awemeId)
        val cookie = CookieStore.cookieString()
        if (cookie.isEmpty()) {
            Log.d(TAG, "no cookie")
            return null
        }
        for (aid in aids) {
            val params = buildParams(awemeId, aid)
            val query = params.entries.joinToString("&") { (k, v) ->
                "$k=${URLEncoder.encode(v, "UTF-8")}"
            }
            val aBogus = generateABogus(query) ?: continue
            val signed = "$query&a_bogus=${URLEncoder.encode(aBogus, "UTF-8")}"
            val url = "https://www.douyin.com/aweme/v1/web/aweme/detail/?$signed"
            val referer = if (aid == "1128") {
                "https://www.douyin.com/video/$awemeId"
            } else {
                "https://www.douyin.com/note/$awemeId"
            }
            val body = requestWithRetry(url, cookie, referer, 3)
            if (body == null) {
                Log.d(TAG, "aid=$aid empty/error, try next")
                continue
            }
            val formatted = parseDetail(body, awemeId)
            if (formatted != null) return formatted
        }
        return null
    }

    private fun determineAids(awemeId: String): Array<String> {
        return AID_VIDEO // 1128 优先；若图文会被 6383 兜底
    }

    private fun buildParams(awemeId: String, aid: String): LinkedHashMap<String, String> {
        val msToken = extractCookie("msToken")
        val p = LinkedHashMap<String, String>()
        p["device_platform"] = "webapp"
        p["aid"] = aid
        p["channel"] = "channel_pc_web"
        p["update_version_code"] = "170400"
        p["pc_client_type"] = "1"
        p["pc_libra_divert"] = "Windows"
        p["version_code"] = "290100"
        p["version_name"] = "29.1.0"
        p["cookie_enabled"] = "true"
        p["screen_width"] = "1536"
        p["screen_height"] = "864"
        p["browser_language"] = "zh-CN"
        p["browser_platform"] = "Win32"
        p["browser_name"] = "Chrome"
        p["browser_version"] = "139.0.0.0"
        p["browser_online"] = "true"
        p["engine_name"] = "Blink"
        p["engine_version"] = "139.0.0.0"
        p["os_name"] = "Windows"
        p["os_version"] = "10"
        p["cpu_core_num"] = "16"
        p["device_memory"] = "8"
        p["platform"] = "PC"
        p["downlink"] = "10"
        p["effective_type"] = "4g"
        p["round_trip_time"] = "200"
        p["support_h265"] = "1"
        p["support_dash"] = "1"
        p["uifid"] = ""
        if (msToken.isNotEmpty()) p["msToken"] = msToken
        p["aweme_id"] = awemeId
        return p
    }

    private fun extractCookie(name: String): String {
        val c = CookieStore.cookieString()
        for (part in c.split(";")) {
            val t = part.trim()
            if (t.startsWith("$name=")) return t.substring(name.length + 1)
        }
        return ""
    }

    private fun requestWithRetry(url: String, cookie: String, referer: String, maxRetries: Int): String? {
        var lastBody: String? = null
        for (attempt in 0 until maxRetries) {
            lastBody = requestOnce(url, cookie, referer)
            if (lastBody != null) return lastBody
            if (attempt < maxRetries - 1) {
                val delay = when (attempt) { 0 -> 1000L; 1 -> 2000L; else -> 5000L }
                Thread.sleep(delay)
            }
        }
        return lastBody
    }

    private fun requestOnce(url: String, cookie: String, referer: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            conn.setRequestProperty("Referer", referer)
            conn.setRequestProperty("Cookie", cookie)
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.d(TAG, "detail http $code")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (body.isEmpty()) {
                Log.d(TAG, "detail empty 200 (anti-bot)")
                return null
            }
            body
        } catch (e: Exception) {
            Log.d(TAG, "detail req fail: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 通过 WebView 执行打包后的 abogus.js，返回 a_bogus 签名。
     * 使用 evaluateJavascript 同步等待（最多 3 秒）。
     */
    private fun generateABogus(query: String): String? {
        var result: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        val js = buildSignJs(query)
        signWebView.post {
            signWebView.evaluateJavascript(js) { value ->
                result = unquote(value)
                latch.countDown()
            }
        }
        try {
            if (!latch.await(4000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                Log.d(TAG, "abogus timeout")
                return null
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        }
        if (result == null || result!!.length < 40) {
            Log.d(TAG, "abogus bad: $result")
            return null
        }
        return result
    }

    private fun buildSignJs(query: String): String {
        val js = readAsset()
        return "$js;JSON.stringify(__abogus.generateABogus(" +
            "${quote(query)}, ${quote(UA)}));"
    }

    private fun quote(s: String): String = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

    private fun readAsset(): String {
        return try {
            val input = context.assets.open("abogus/abogus.js")
            input.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    private fun unquote(s: String?): String? {
        if (s == null) return null
        return try {
            val v = org.json.JSONTokener(s).nextValue()
            if (v is String) v else s
        } catch (e: Exception) {
            s
        }
    }

    private fun parseDetail(body: String, awemeId: String): String? {
        val root = try { JSONObject(body) } catch (e: Exception) {
            Log.d(TAG, "parse not json: ${body.take(120)}")
            return null
        }
        val aweme = root.optJSONObject("aweme_detail")
        if (aweme == null) {
            Log.d(TAG, "no aweme_detail status=${root.opt("status_code")}")
            return null
        }
        if (aweme.optString("aweme_id", "") != awemeId) {
            Log.d(TAG, "mismatch id ${aweme.optString("aweme_id")}")
        }

        val videoUrls = mutableListOf<String>()
        val coverUrls = mutableListOf<String>()
        val video = aweme.optJSONObject("video")
        if (video != null) {
            val playAddr = video.optJSONObject("play_addr")
            playAddr?.optJSONArray("url_list")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotEmpty() }?.let { videoUrls.add(it) }
                }
            }
            if (videoUrls.isEmpty()) {
                val uri = playAddr?.optString("uri", "") ?: ""
                if (uri.isNotEmpty()) {
                    videoUrls.add("https://aweme.snssdk.com/aweme/v1/play/?video_id=$uri&ratio=1080p&line=0")
                }
            }
            for (k in listOf("cover", "origin_cover", "dynamic_cover")) {
                video.optJSONObject(k)?.optJSONArray("url_list")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotEmpty() }?.let { coverUrls.add(it) }
                    }
                }
            }
        }

        val imageUrls = mutableListOf<String>()
        val images = aweme.optJSONArray("images")
        if (images != null) for (i in 0 until images.length()) {
            val im = images.optJSONObject(i)
            val u = im?.optJSONArray("url_list")?.optString(0, "") ?: ""
            if (u.isNotEmpty()) imageUrls.add(u)
        }

        val author = aweme.optJSONObject("author") ?: JSONObject()
        val stats = aweme.optJSONObject("statistics") ?: JSONObject()

        // 检查合集信息 - 可能存在于顶层或 mix_info 子对象中
        val mixId = aweme.optString("mix_id", "").takeIf { it.isNotEmpty() }
            ?: aweme.optJSONObject("mix_info")?.optString("mix_id", "")
        val mixInfoObj = aweme.optJSONObject("mix_info")

        // 检查 series_info 字段（新版 API 可能使用此字段）
        val seriesInfo = aweme.optJSONObject("series_info")
        val seriesBasicInfo = aweme.optJSONObject("series_basic_info")
        val chapterList = aweme.optJSONArray("chapter_list")

        Log.d(TAG, "parseDetail: mix_id(top)=${aweme.optString("mix_id", "null")}, mix_info=${mixInfoObj?.toString()?.take(200) ?: "null"}")

        // 尝试从 series_info 或 series_basic_info 提取合集信息
        val finalMixId = mixId
            ?: seriesBasicInfo?.optString("series_id", "")?.takeIf { it.isNotEmpty() }
            ?: seriesInfo?.optString("series_id", "")?.takeIf { it.isNotEmpty() }

        // 合集名称：mix_info.mix_name 优先，series_info.series_name 兜底（短剧）
        val mixName = mixInfoObj?.optString("mix_name", "")
            ?.takeIf { it.isNotEmpty() }
            ?: seriesInfo?.optString("series_name", "")
                ?.removePrefix("短剧 · ")
                ?.takeIf { it.isNotEmpty() }
            ?: ""

        val mixStats = mixInfoObj?.optJSONObject("statis") ?: seriesInfo?.optJSONObject("stats")

        return JSONObject().apply {
            put("ok", true)
            put("url", "")
            put("awemeId", aweme.optString("aweme_id", awemeId))
            put("desc", aweme.optString("desc", ""))
            put("authorName", author.optString("nickname", ""))
            put("authorUniqueId", author.optString("unique_id", ""))
            put("videoUrlList", JSONArray(videoUrls))
            put("coverUrlList", JSONArray(coverUrls))
            put("images", JSONArray(imageUrls))
            put("digg", stats.optLong("digg_count", 0))
            put("comment", stats.optLong("comment_count", 0))
            put("share", stats.optLong("share_count", 0))

            // 合集信息
            if (!finalMixId.isNullOrEmpty()) {
                put("mixId", finalMixId)
                put("mixName", mixName)
                put("totalEpisode", mixStats?.optInt("total_episode", 0) ?: 0)
                put("currentEpisode", mixStats?.optInt("current_episode", 0) ?: 0)
            }
        }.toString()
    }

    /**
     * 提取视频详情中的合集 ID
     */
    fun extractMixId(awemeJson: String): String? {
        return try {
            val root = JSONObject(awemeJson)
            val mixId = root.optString("mixId", "")
            mixId.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取合集详情
     */
    fun fetchMixDetail(mixId: String): MixInfo? {
        val cookie = CookieStore.cookieString()
        if (cookie.isEmpty()) {
            Log.d(TAG, "no cookie for mix detail")
            return null
        }

        val params = buildMixParams(mixId)
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        val aBogus = generateABogus(query) ?: return null
        val signed = "$query&a_bogus=${URLEncoder.encode(aBogus, "UTF-8")}"
        val url = "https://www.douyin.com/aweme/v1/web/mix/detail/?$signed"

        // mix/detail 常被风控 403，只试 1 次，失败即返回 null（信息可由 detail 解析兜底）
        val body = requestWithRetry(url, cookie, "https://www.douyin.com/", 1) ?: return null
        return AwemeParser.parseMixInfo(body)
    }

    /**
     * 分页获取合集内视频列表
     */
    fun fetchMixAwemeList(mixId: String, cursor: Long = 0): Triple<List<MixEpisode>, Long, Boolean> {
        val cookie = CookieStore.cookieString()
        if (cookie.isEmpty()) {
            Log.d(TAG, "no cookie for mix aweme list")
            return Triple(emptyList(), 0, false)
        }

        val params = buildMixAwemeParams(mixId, cursor)
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        val aBogus = generateABogus(query)
        if (aBogus == null) {
            Log.d(TAG, "mix aweme abogus null")
            return Triple(emptyList(), 0, false)
        }
        val signed = "$query&a_bogus=${URLEncoder.encode(aBogus, "UTF-8")}"
        val url = "https://www.douyin.com/aweme/v1/web/mix/aweme/?$signed"

        val body = requestWithRetry(url, cookie, "https://www.douyin.com/", 3)
        if (body == null) {
            Log.d(TAG, "mix aweme body null")
            return Triple(emptyList(), 0, false)
        }
        return AwemeParser.parseMixAwemeList(body)
    }

    /**
     * 分页获取合集内所有视频。
     * onProgress 可选回调：已拉取的集数 / 单页返回的条数。
     */
    fun fetchAllMixAwemeList(mixId: String, onProgress: ((Int) -> Unit)? = null): List<MixEpisode> {
        val allEpisodes = mutableListOf<MixEpisode>()
        var cursor = 0L
        var hasMore = true

        while (hasMore) {
            val (episodes, nextCursor, more) = fetchMixAwemeList(mixId, cursor)
            allEpisodes.addAll(episodes)
            hasMore = more && episodes.isNotEmpty() && nextCursor > cursor
            cursor = nextCursor

            onProgress?.invoke(allEpisodes.size)

            // 避免请求过快触发风控
            if (hasMore) {
                Thread.sleep(500)
            }
        }

        return allEpisodes
    }

    private fun buildMixParams(mixId: String): LinkedHashMap<String, String> {
        val msToken = extractCookie("msToken")
        val p = LinkedHashMap<String, String>()
        p["device_platform"] = "webapp"
        p["aid"] = "6383"
        p["channel"] = "channel_pc_web"
        p["update_version_code"] = "170400"
        p["pc_client_type"] = "1"
        p["pc_libra_divert"] = "Windows"
        p["version_code"] = "290100"
        p["version_name"] = "29.1.0"
        p["cookie_enabled"] = "true"
        p["screen_width"] = "1536"
        p["screen_height"] = "864"
        p["browser_language"] = "zh-CN"
        p["browser_platform"] = "Win32"
        p["browser_name"] = "Chrome"
        p["browser_version"] = "139.0.0.0"
        p["browser_online"] = "true"
        p["engine_name"] = "Blink"
        p["engine_version"] = "139.0.0.0"
        p["os_name"] = "Windows"
        p["os_version"] = "10"
        p["cpu_core_num"] = "16"
        p["device_memory"] = "8"
        p["platform"] = "PC"
        p["downlink"] = "10"
        p["effective_type"] = "4g"
        p["round_trip_time"] = "200"
        p["support_h265"] = "1"
        p["support_dash"] = "1"
        p["uifid"] = ""
        if (msToken.isNotEmpty()) p["msToken"] = msToken
        p["mix_id"] = mixId
        return p
    }

    private fun buildMixAwemeParams(mixId: String, cursor: Long): LinkedHashMap<String, String> {
        val msToken = extractCookie("msToken")
        val p = LinkedHashMap<String, String>()
        p["device_platform"] = "webapp"
        p["aid"] = "6383"
        p["channel"] = "channel_pc_web"
        p["update_version_code"] = "170400"
        p["pc_client_type"] = "1"
        p["pc_libra_divert"] = "Windows"
        p["version_code"] = "290100"
        p["version_name"] = "29.1.0"
        p["cookie_enabled"] = "true"
        p["screen_width"] = "1536"
        p["screen_height"] = "864"
        p["browser_language"] = "zh-CN"
        p["browser_platform"] = "Win32"
        p["browser_name"] = "Chrome"
        p["browser_version"] = "139.0.0.0"
        p["browser_online"] = "true"
        p["engine_name"] = "Blink"
        p["engine_version"] = "139.0.0.0"
        p["os_name"] = "Windows"
        p["os_version"] = "10"
        p["cpu_core_num"] = "16"
        p["device_memory"] = "8"
        p["platform"] = "PC"
        p["downlink"] = "10"
        p["effective_type"] = "4g"
        p["round_trip_time"] = "200"
        p["support_h265"] = "1"
        p["support_dash"] = "1"
        p["uifid"] = ""
        if (msToken.isNotEmpty()) p["msToken"] = msToken
        p["mix_id"] = mixId
        p["cursor"] = cursor.toString()
        p["count"] = "20"
        return p
    }
}