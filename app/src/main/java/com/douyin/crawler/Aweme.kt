package com.douyin.crawler

import org.json.JSONObject

data class Aweme(
    val awemeId: String,
    val desc: String,
    val authorName: String,
    val authorUniqueId: String,
    val videoUrls: List<String>,
    val coverUrls: List<String>,
    val imageUrls: List<String>,
    val stats: JSONObject,
    val mixId: String? = null,
    val mixName: String? = null,
    val totalEpisode: Int = 0,
    val currentEpisode: Int = 0
) {
    val type: String
        get() = if (imageUrls.isNotEmpty()) "images" else "video"

    val typeLabel: String
        get() = if (type == "video") "视频" else "图集"

    val primaryVideoUrl: String?
        get() = videoUrls.firstOrNull()

    val primaryCoverUrl: String?
        get() = coverUrls.firstOrNull()

    val hasMix: Boolean
        get() = !mixId.isNullOrEmpty()
}

data class MixInfo(
    val mixId: String,
    val mixName: String,
    val authorName: String,
    val totalEpisode: Int
)

data class MixEpisode(
    val awemeId: String,
    val desc: String,
    val coverUrl: String,
    val videoUrl: String,
    val episodeIndex: Int
)