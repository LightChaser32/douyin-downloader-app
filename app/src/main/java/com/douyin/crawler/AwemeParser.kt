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
                stats = stats
            )
        } catch (e: Exception) {
            null
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