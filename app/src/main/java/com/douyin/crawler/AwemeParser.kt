package com.douyin.crawler

import org.json.JSONArray
import org.json.JSONObject

object AwemeParser {

    fun parse(jsResult: String): Aweme? {
        return try {
            val root = JSONObject(jsResult)
            if (!root.optBoolean("ok", false)) return null
            val awemeId = root.optString("awemeId", "")
            if (awemeId.isEmpty()) return null

            val stats = JSONObject()
            stats.put("digg", root.optLong("digg", 0))
            stats.put("comment", root.optLong("comment", 0))
            stats.put("share", root.optLong("share", 0))

            Aweme(
                awemeId = awemeId,
                desc = root.optString("desc", ""),
                authorName = root.optString("authorName", ""),
                authorUniqueId = root.optString("authorUniqueId", ""),
                videoUrls = stringList(root.optJSONArray("videoUrlList")),
                coverUrls = stringList(root.optJSONArray("coverUrlList")),
                imageUrls = stringList(root.optJSONArray("images")),
                stats = stats,
                mixId = root.optString("mixId", "").takeIf { it.isNotEmpty() },
                mixName = root.optString("mixName", "").takeIf { it.isNotEmpty() },
                totalEpisode = root.optInt("totalEpisode", 0),
                currentEpisode = root.optInt("currentEpisode", 0)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseMixInfo(json: String): MixInfo? {
        return try {
            val root = JSONObject(json)
            val mixInfo = root.optJSONObject("mix_info") ?: return null
            val mixId = mixInfo.optString("mix_id", "")
            if (mixId.isEmpty()) return null

            val author = mixInfo.optJSONObject("author") ?: JSONObject()
            MixInfo(
                mixId = mixId,
                mixName = mixInfo.optString("mix_name", "未知合集"),
                authorName = author.optString("nickname", "未知作者"),
                totalEpisode = mixInfo.optJSONObject("statis")?.optInt("total_episode", 0) ?: 0
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseMixAwemeList(json: String): Triple<List<MixEpisode>, Long, Boolean> {
        val episodes = mutableListOf<MixEpisode>()
        var nextCursor = 0L
        var hasMore = false

        try {
            val root = JSONObject(json)
            hasMore = root.optInt("has_more", 0) == 1
            nextCursor = root.optLong("cursor", root.optLong("max_cursor", 0))
            val awemeList = root.optJSONArray("aweme_list")
            if (awemeList == null) {
                return Triple(episodes, nextCursor, false)
            }

            val episodeMap = HashMap<String, Int>()
            try {
                val raw = root.optString("item_id_to_episode", "")
                if (raw.isNotEmpty()) {
                    val map = JSONObject(raw)
                    val it = map.keys()
                    while (it.hasNext()) {
                        val key = it.next()
                        episodeMap[key] = map.optInt(key, 0)
                    }
                }
            } catch (e: Exception) { }

            for (i in 0 until awemeList.length()) {
                val item = awemeList.optJSONObject(i) ?: continue
                val awemeId = item.optString("aweme_id", "")
                if (awemeId.isEmpty()) continue

                val desc = item.optString("desc", "无标题")
                val coverUrl = extractCoverUrl(item)
                val videoUrl = extractVideoUrl(item)
                val stats = item.optJSONObject("statistics")

                episodes.add(
                    MixEpisode(
                        awemeId = awemeId,
                        desc = desc,
                        coverUrl = coverUrl,
                        videoUrl = videoUrl,
                        episodeIndex = episodeMap[awemeId] ?: (i + 1)
                    )
                )
            }
        } catch (e: Exception) {
        }

        return Triple(episodes, nextCursor, hasMore)
    }

    private fun extractCoverUrl(item: JSONObject): String {
        val video = item.optJSONObject("video") ?: return ""

        // 优先原图封面
        val originCover = video.optJSONObject("origin_cover")
        val urlList = originCover?.optJSONArray("url_list")
        if (urlList != null && urlList.length() > 0) {
            return urlList.optString(0, "")
        }

        // 备选普通封面
        val cover = video.optJSONObject("cover")
        val coverList = cover?.optJSONArray("url_list")
        if (coverList != null && coverList.length() > 0) {
            return coverList.optString(0, "")
        }

        return ""
    }

    private fun extractVideoUrl(item: JSONObject): String {
        val video = item.optJSONObject("video") ?: return ""
        val playAddr = video.optJSONObject("play_addr") ?: return ""
        val urlList = playAddr.optJSONArray("url_list") ?: return ""

        // 分类：直连CDN vs play端点
        val directUrls = mutableListOf<String>()
        val playUrls = mutableListOf<String>()

        for (i in 0 until urlList.length()) {
            val url = urlList.optString(i, "")
            if (url.isEmpty()) continue
            if (url.contains("watermark=1")) continue

            if (url.contains("douyin.com")) {
                playUrls.add(url)
            } else {
                directUrls.add(url)
            }
        }

        // 优先级：直连CDN > play端点 > 第一个
        return when {
            directUrls.isNotEmpty() -> directUrls[0]
            playUrls.isNotEmpty() -> playUrls[0]
            urlList.length() > 0 -> urlList.optString(0, "")
            else -> ""
        }
    }

    private fun stringList(arr: JSONArray?): List<String> {
        val out = ArrayList<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotEmpty()) out.add(s)
            }
        }
        return out
    }
}