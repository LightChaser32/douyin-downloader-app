package com.douyin.crawler

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.FrameLayout
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var inputEdit: EditText
    private lateinit var parseBtn: Button
    private lateinit var resultCard: LinearLayout
    private lateinit var coverView: ImageView
    private lateinit var resultTitle: TextView
    private lateinit var resultAuthor: TextView
    private lateinit var resultType: TextView
    private lateinit var imageStrip: HorizontalScrollView
    private lateinit var imageStripContent: LinearLayout
    private lateinit var selectAllBtn: Button
    private lateinit var btnDownload: Button
    private lateinit var progressWrap: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var webView: WebView

    // 合集相关视图
    private lateinit var mixInfoCard: LinearLayout
    private lateinit var mixName: TextView
    private lateinit var mixAuthor: TextView
    private lateinit var mixEpisodeCount: TextView
    private lateinit var btnMixSelectAll: Button
    private lateinit var btnMixDownload: Button
    private lateinit var btnMixToggleList: Button
    private lateinit var mixProgressWrap: LinearLayout
    private lateinit var mixProgressBar: ProgressBar
    private lateinit var mixProgressText: TextView
    private lateinit var mixEpisodeList: LinearLayout
    private lateinit var mixPagerWrap: LinearLayout
    private lateinit var btnMixPrevPage: Button
    private lateinit var btnMixNextPage: Button
    private lateinit var mixPageText: TextView

    private lateinit var loader: WebViewLoader
    private lateinit var api: ApiClient
    private lateinit var storage: LocalStorage

    private var currentAweme: Aweme? = null
    private var downloading = false
    private var downloadingImageCount = 0
    private var downloadedImageCount = 0
    private val selectedImages = mutableListOf<Boolean>()

    // 合集相关数据
    private var currentMixInfo: MixInfo? = null
    private var mixEpisodes = mutableListOf<MixEpisode>()
    private val selectedMixEpisodes = mutableListOf<Boolean>()
    private var mixListExpanded = false
    private var downloadingMix = false
    private var downloadingMixIndex = 0

    // 合集列表分页
    private var mixPageIndex = 0
    private val mixPageSize = 20

    private val io: ExecutorService = Executors.newFixedThreadPool(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        CookieStore.init(this)
        storage = LocalStorage(this)
        bindViews()
        setupWebView()
        setupActions()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = extractUrl(sharedText)
        if (url == null) {
            Toast.makeText(this, "未在分享内容中找到抖音链接", Toast.LENGTH_LONG).show()
            return
        }
        inputEdit.setText(url)
        inputEdit.setSelection(url.length)
        startParse()
    }

    private fun bindViews() {
        inputEdit = findViewById(R.id.inputEdit)
        parseBtn = findViewById(R.id.parseBtn)
        resultCard = findViewById(R.id.resultCard)
        coverView = findViewById(R.id.coverView)
        resultTitle = findViewById(R.id.resultTitle)
        resultAuthor = findViewById(R.id.resultAuthor)
        resultType = findViewById(R.id.resultType)
        imageStrip = findViewById(R.id.imageStrip)
        imageStripContent = findViewById(R.id.imageStripContent)
        selectAllBtn = findViewById(R.id.selectAllBtn)
        btnDownload = findViewById(R.id.btnDownload)
        progressWrap = findViewById(R.id.progressWrap)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        webView = findViewById(R.id.webView)

        // 合集相关视图
        mixInfoCard = findViewById(R.id.mixInfoCard)
        mixName = findViewById(R.id.mixName)
        mixAuthor = findViewById(R.id.mixAuthor)
        mixEpisodeCount = findViewById(R.id.mixEpisodeCount)
        btnMixSelectAll = findViewById(R.id.btnMixSelectAll)
        btnMixDownload = findViewById(R.id.btnMixDownload)
        btnMixToggleList = findViewById(R.id.btnMixToggleList)
        mixProgressWrap = findViewById(R.id.mixProgressWrap)
        mixProgressBar = findViewById(R.id.mixProgressBar)
        mixProgressText = findViewById(R.id.mixProgressText)
        mixEpisodeList = findViewById(R.id.mixEpisodeList)
        mixPagerWrap = findViewById(R.id.mixPagerWrap)
        btnMixPrevPage = findViewById(R.id.btnMixPrevPage)
        btnMixNextPage = findViewById(R.id.btnMixNextPage)
        mixPageText = findViewById(R.id.mixPageText)
    }

    private fun setupWebView() {
        loader = WebViewLoader(this, webView)
        loader.listener = object : WebViewLoader.Listener {
            override fun onParsed(rawJson: String?) {
                parseBtn.isEnabled = true
                parseBtn.text = getString(R.string.btn_parse)
                if (rawJson == null) {
                    android.util.Log.d("DouyinMain", "onParsed null")
                    Toast.makeText(this@MainActivity, "未能解析数据，请重试或检查链接", Toast.LENGTH_SHORT).show()
                    return
                }
                val aweme = AwemeParser.parse(rawJson)
                if (aweme == null) {
                    val err = try {
                        org.json.JSONObject(rawJson).optString("error", rawJson.take(120))
                    } catch (e: Exception) {
                        rawJson.take(120)
                    }
                    android.util.Log.d("DouyinMain", "parse failed: $err")
                    Toast.makeText(this@MainActivity, "解析失败: $err", Toast.LENGTH_SHORT).show()
                    return
                }
                showResult(aweme)
            }

            override fun onState(text: String) {
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
            }

            override fun onLoginRequired() {
                Toast.makeText(this@MainActivity, R.string.need_login, Toast.LENGTH_LONG).show()
            }
        }
        loader.init()
        api = ApiClient(this, webView)
    }

    private fun setupActions() {
        parseBtn.setOnClickListener { startParse() }
        inputEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                startParse()
                true
            } else false
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { showSettings() }
        btnDownload.setOnClickListener { startDownload() }
        selectAllBtn.setOnClickListener { toggleSelectAll() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { inputEdit.setText("") }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyLink() }

        // 合集相关按钮
        btnMixSelectAll.setOnClickListener { toggleMixSelectAll() }
        btnMixDownload.setOnClickListener { startMixDownload() }
        btnMixToggleList.setOnClickListener { toggleMixEpisodeList() }
        btnMixPrevPage.setOnClickListener { mixPageIndex--; updateMixEpisodeList() }
        btnMixNextPage.setOnClickListener { mixPageIndex++; updateMixEpisodeList() }
        mixPageText.setOnClickListener { showMixPageDialog() }
    }

    private fun copyLink() {
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this).toString()
        } else ""
        if (text.isEmpty()) {
            Toast.makeText(this, "剪切板为空", Toast.LENGTH_SHORT).show()
            return
        }
        inputEdit.setText(text)
        inputEdit.setSelection(text.length)
        Toast.makeText(this, "已粘贴", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSelectAll() {
        if (selectedImages.isEmpty()) return
        val allSelected = selectedImages.all { it }
        val target = !allSelected
        for (i in selectedImages.indices) selectedImages[i] = target
        updateCheckBoxes(target)
        selectAllBtn.text = getString(if (target) R.string.select_none else R.string.select_all)
    }

    private fun updateCheckBoxes(target: Boolean) {
        val content = imageStripContent
        for (i in 0 until content.childCount) {
            val wrap = content.getChildAt(i) as? FrameLayout ?: continue
            val cb = wrap.getChildAt(1) as? CheckBox ?: continue
            cb.isChecked = target
        }
    }

    private fun startParse() {
        val raw = inputEdit.text.toString().trim()
        if (raw.isEmpty()) {
            Toast.makeText(this, "请先粘贴抖音分享链接", Toast.LENGTH_SHORT).show()
            return
        }
        val url = extractUrl(raw)
        if (url == null) {
            Toast.makeText(this, "未识别到抖音链接，请检查分享内容", Toast.LENGTH_SHORT).show()
            return
        }
        currentAweme = null
        currentMixInfo = null
        mixEpisodes.clear()
        selectedMixEpisodes.clear()
        resultCard.visibility = View.GONE
        imageStrip.visibility = View.GONE
        selectAllBtn.visibility = View.GONE
        progressWrap.visibility = View.GONE
        mixInfoCard.visibility = View.GONE
        mixEpisodeList.visibility = View.GONE
        mixListExpanded = false
        parseBtn.isEnabled = false
        parseBtn.text = getString(R.string.parsing)
        resolveAndLoad(url)
    }

    private fun resolveAndLoad(url: String) {
        Thread {
            try {
                val awemeId = api.resolveAwemeId(url)
                if (awemeId == null) {
                    runOnUiThread {
                        parseBtn.isEnabled = true
                        parseBtn.text = getString(R.string.btn_parse)
                        Toast.makeText(this, "未解析出作品 ID，请检查链接", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }
                android.util.Log.d("DouyinMain", "resolved aweme_id=$awemeId")
                if (!CookieStore.hasLogin()) {
                    runOnUiThread {
                        parseBtn.isEnabled = true
                        parseBtn.text = getString(R.string.btn_parse)
                        Toast.makeText(this, "请先登录抖音（设置 → 打开登录页）", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }
                val rawJson = api.fetchDetail(awemeId)
                runOnUiThread {
                    parseBtn.isEnabled = true
                    parseBtn.text = getString(R.string.btn_parse)
                    if (rawJson != null) {
                        val aweme = AwemeParser.parse(rawJson)
                        if (aweme != null) {
                            showResult(aweme)
                            // 检查合集
                            if (storage.isMixParseEnabled() && aweme.hasMix) {
                                loadMixInfo(aweme.mixId!!, aweme.mixName, aweme.totalEpisode)
                            }
                        } else {
                            Toast.makeText(this, "解析结果无效，请重试", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "获取数据失败（可能需重新登录）", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.d("DouyinMain", "detail parse err: ${e.message}")
                runOnUiThread {
                    parseBtn.isEnabled = true
                    parseBtn.text = getString(R.string.btn_parse)
                    Toast.makeText(this, "解析异常: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun extractUrl(text: String): String? {
        val httpMatch = Regex("https?://[^\\s，。、；;！!]+").find(text)
        if (httpMatch != null) {
            val u = httpMatch.value.trimEnd('，', '。', ',', '.', '）', ')', ']', '/')
            if (u.contains("douyin.com")) return u + "/"
        }
        val shortMatch = Regex("(?:https?://)?v\\.douyin\\.com/[^\\s，。、；;！!]+").find(text)
        if (shortMatch != null) {
            val u = shortMatch.value.trimEnd('，', '。', ',', '.', '）', ')', ']', '/')
            return if (u.startsWith("http")) u + "/" else "https://$u/"
        }
        return null
    }

    private fun showResult(aweme: Aweme) {
        currentAweme = aweme
        resultCard.visibility = View.VISIBLE
        resultTitle.text = aweme.desc.ifEmpty { "(无标题)" }
        resultAuthor.text = "@${aweme.authorName.ifEmpty { aweme.authorUniqueId.ifEmpty { "未知作者" } }}"
        val digg = aweme.stats.optLong("digg", 0)
        resultType.text = "${aweme.typeLabel} · 点赞 ${fmtCount(digg)}"

        aweme.primaryCoverUrl?.let { loadBitmap(it) { bmp -> if (bmp != null) coverView.setImageBitmap(bmp) } }

        if (aweme.type == "images") {
            imageStrip.visibility = View.VISIBLE
            imageStripContent.removeAllViews()
            selectedImages.clear()
            aweme.imageUrls.forEachIndexed { index, url ->
                selectedImages.add(true)
                val wrap = FrameLayout(this)
                wrap.layoutParams = LinearLayout.LayoutParams(dp(92), dp(122)).apply {
                    setMargins(0, 0, dp(8), 0)
                }
                val iv = ImageView(this)
                iv.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.background = getDrawable(android.R.drawable.ic_menu_gallery)
                wrap.addView(iv)
                val cb = CheckBox(this)
                cb.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP or android.view.Gravity.END
                )
                cb.isChecked = true
                cb.buttonTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
                cb.setOnCheckedChangeListener { _, checked -> selectedImages[index] = checked }
                wrap.addView(cb)
                imageStripContent.addView(wrap)
                loadBitmap(url) { bmp -> if (bmp != null) iv.setImageBitmap(bmp) }
            }
            selectAllBtn.visibility = View.VISIBLE
            selectAllBtn.text = getString(R.string.select_none)
        } else {
            imageStrip.visibility = View.GONE
            selectAllBtn.visibility = View.GONE
        }

        btnDownload.text = getString(if (aweme.type == "video") R.string.download_video else R.string.download_images)
        btnDownload.isEnabled = true
    }

    // 合集相关方法
    private fun loadMixInfo(mixId: String, knownName: String? = null, knownEpisodes: Int = 0) {
        mixInfoCard.visibility = View.VISIBLE
        mixName.text = if (!knownName.isNullOrEmpty()) "《$knownName》" else getString(R.string.mix_loading)
        mixAuthor.text = ""
        mixEpisodeCount.text = if (knownEpisodes > 0) {
            getString(R.string.mix_episodes, knownEpisodes)
        } else {
            ""
        }
        btnMixDownload.isEnabled = knownEpisodes > 0
        btnMixToggleList.isEnabled = true

        // 显示解析进度
        mixProgressWrap.visibility = View.VISIBLE
        mixProgressBar.isIndeterminate = true
        mixProgressText.text = getString(R.string.mix_parsing)

        io.execute {
            // 先用 mix/aweme 加载列表（detail 接口的 mix/detail 常被风控 403，这里不再强依赖）
            try {
                val episodes = api.fetchAllMixAwemeList(mixId) { loaded ->
                    runOnUiThread {
                        if (knownEpisodes > 0) {
                            mixProgressBar.isIndeterminate = false
                            mixProgressBar.max = knownEpisodes
                            mixProgressBar.progress = loaded
                            mixProgressText.text = getString(R.string.mix_parsing_progress, loaded, knownEpisodes)
                        } else {
                            mixProgressText.text = getString(R.string.mix_parsing_unknown, loaded)
                        }
                    }
                }
                if (episodes.isEmpty()) {
                    runOnUiThread {
                        mixProgressWrap.visibility = View.GONE
                        mixName.text = getString(R.string.mix_load_error)
                    }
                    return@execute
                }
                // 尝试获取更完整的合集信息（可选，失败不影响列表）
                val mixInfo = try { api.fetchMixDetail(mixId) } catch (e: Exception) { null }
                val displayName = mixInfo?.mixName?.takeIf { it.isNotEmpty() } ?: knownName ?: ""
                val totalEpisode = mixInfo?.totalEpisode?.takeIf { it > 0 } ?: knownEpisodes
                val authorName = mixInfo?.authorName ?: ""

                runOnUiThread {
                    currentMixInfo = MixInfo(
                        mixId = mixId,
                        mixName = displayName,
                        authorName = authorName,
                        totalEpisode = totalEpisode
                    )
                    mixProgressWrap.visibility = View.GONE
                    mixName.text = if (displayName.isNotEmpty()) "《$displayName》" else getString(R.string.mix_loading)
                    mixAuthor.text = if (authorName.isNotEmpty()) "作者: $authorName" else ""
                    mixEpisodeCount.text = if (totalEpisode > 0) {
                        getString(R.string.mix_episodes, totalEpisode)
                    } else {
                        getString(R.string.mix_episodes, episodes.size)
                    }
                    btnMixDownload.isEnabled = true
                    btnMixToggleList.isEnabled = true
                }
                loadMixEpisodes(mixId, episodes)
            } catch (e: Exception) {
                runOnUiThread {
                    mixProgressWrap.visibility = View.GONE
                    mixName.text = getString(R.string.mix_load_error)
                }
            }
        }
    }

    private fun loadMixEpisodes(mixId: String, preFetched: List<MixEpisode>? = null) {
        runOnUiThread {
            mixProgressWrap.visibility = View.VISIBLE
            mixProgressText.text = "加载中..."
        }

        io.execute {
            try {
                val episodes = preFetched ?: api.fetchAllMixAwemeList(mixId)
                runOnUiThread {
                    mixProgressWrap.visibility = View.GONE
                    mixEpisodes.clear()
                    mixEpisodes.addAll(episodes)
                    selectedMixEpisodes.clear()
                    repeat(episodes.size) { selectedMixEpisodes.add(true) }
                    btnMixSelectAll.text = getString(R.string.select_none)
                    mixPageIndex = 0
                    updateMixEpisodeList()
                    // 加载完成后自动展开选集列表，方便直接查看/选择
                    mixListExpanded = true
                    mixEpisodeList.visibility = View.VISIBLE
                    btnMixToggleList.text = getString(R.string.mix_collapse_list)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    mixProgressWrap.visibility = View.GONE
                    mixProgressText.text = "加载失败"
                }
            }
        }
    }

    private fun updateMixEpisodeList() {
        mixEpisodeList.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val pageCount = (mixEpisodes.size + mixPageSize - 1) / mixPageSize
        if (mixPageIndex >= pageCount) mixPageIndex = (pageCount - 1).coerceAtLeast(0)
        val start = mixPageIndex * mixPageSize
        val end = (start + mixPageSize).coerceAtMost(mixEpisodes.size)
        val pageEpisodes = mixEpisodes.subList(start, end)

        pageEpisodes.forEachIndexed { offset, episode ->
            val index = start + offset
            val itemView = inflater.inflate(R.layout.item_mix_episode, mixEpisodeList, false)
            val checkBox = itemView.findViewById<CheckBox>(R.id.mixEpisodeCheckBox)
            val cover = itemView.findViewById<ImageView>(R.id.mixEpisodeCover)
            val title = itemView.findViewById<TextView>(R.id.mixEpisodeTitle)
            val episodeIndex = itemView.findViewById<TextView>(R.id.mixEpisodeIndex)

            title.text = episode.desc.ifEmpty { "第 ${episode.episodeIndex} 集" }
            episodeIndex.text = "第 ${episode.episodeIndex} 集"
            checkBox.isChecked = selectedMixEpisodes[index]
            checkBox.setOnCheckedChangeListener { _, isChecked -> selectedMixEpisodes[index] = isChecked }

            if (episode.coverUrl.isNotEmpty()) {
                loadBitmap(episode.coverUrl) { bmp -> if (bmp != null) cover.setImageBitmap(bmp) }
            }

            mixEpisodeList.addView(itemView)
        }

        updateMixPager(pageCount)
    }

    private fun updateMixPager(pageCount: Int) {
        mixPagerWrap.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        mixPageText.text = getString(R.string.mix_page_info, mixPageIndex + 1, pageCount)
        btnMixPrevPage.isEnabled = mixPageIndex > 0
        btnMixNextPage.isEnabled = mixPageIndex < pageCount - 1
    }

    private fun showMixPageDialog() {
        if (mixEpisodes.isEmpty()) return
        val pageCount = (mixEpisodes.size + mixPageSize - 1) / mixPageSize
        if (pageCount <= 1) return

        val input = android.widget.EditText(this)
        input.hint = "1 - $pageCount"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.setText("${mixPageIndex + 1}")
        input.setSelection(input.text.length)

        android.app.AlertDialog.Builder(this)
            .setTitle("跳转到指定页")
            .setMessage("当前共 $pageCount 页")
            .setView(input)
            .setPositiveButton("跳转") { _, _ ->
                val target = input.text.toString().trim().toIntOrNull()
                if (target == null || target < 1 || target > pageCount) {
                    Toast.makeText(this, "页码需在 1 - $pageCount 之间", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                mixPageIndex = target - 1
                updateMixEpisodeList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toggleMixSelectAll() {
        if (selectedMixEpisodes.isEmpty()) return
        val allSelected = selectedMixEpisodes.all { it }
        val target = !allSelected
        for (i in selectedMixEpisodes.indices) selectedMixEpisodes[i] = target
        btnMixSelectAll.text = getString(if (target) R.string.select_none else R.string.select_all)
        updateMixEpisodeCheckBoxes(target)
    }

    private fun updateMixEpisodeCheckBoxes(target: Boolean) {
        for (i in 0 until mixEpisodeList.childCount) {
            val itemView = mixEpisodeList.getChildAt(i)
            val cb = itemView.findViewById<CheckBox>(R.id.mixEpisodeCheckBox)
            cb.isChecked = target
        }
    }

    private fun toggleMixEpisodeList() {
        mixListExpanded = !mixListExpanded
        mixEpisodeList.visibility = if (mixListExpanded) View.VISIBLE else View.GONE
        btnMixToggleList.text = getString(if (mixListExpanded) R.string.mix_collapse_list else R.string.mix_expand_list)
    }

    private fun startMixDownload() {
        val selected = mutableListOf<Int>()
        selectedMixEpisodes.forEachIndexed { index, checked -> if (checked) selected.add(index) }
        if (selected.isEmpty()) {
            Toast.makeText(this, "请至少选择一集", Toast.LENGTH_SHORT).show()
            return
        }
        if (downloadingMix) return

        downloadingMix = true
        downloadingMixIndex = 0
        btnMixDownload.isEnabled = false
        mixProgressWrap.visibility = View.VISIBLE
        mixProgressBar.progress = 0
        mixProgressText.text = getString(R.string.mix_batch_progress, 0, selected.size)
        Downloader.resetCancel()

        downloadMixEpisode(selected, 0)
    }

    private fun downloadMixEpisode(selected: List<Int>, pos: Int) {
        if (pos >= selected.size) {
            downloadingMix = false
            btnMixDownload.isEnabled = true
            mixProgressText.text = getString(R.string.mix_batch_complete, selected.size, 0)
            return
        }

        val index = selected[pos]
        val episode = mixEpisodes[index]
        if (episode.videoUrl.isEmpty()) {
            // 跳过无视频链接的集
            downloadMixEpisode(selected, pos + 1)
            return
        }

        val target = File(storage.rootDir(), "mix/${currentMixInfo?.mixId ?: "unknown"}/${episode.awemeId}.mp4")
        Downloader.download(episode.videoUrl, target, object : Downloader.Callback {
            override fun onProgress(percent: Int, done: Long, total: Long) {
                val overallProgress = ((pos.toDouble() / selected.size) * 100 + (percent.toDouble() / selected.size)).toInt()
                mixProgressBar.progress = overallProgress.coerceIn(0, 100)
                mixProgressText.text = getString(R.string.mix_batch_progress, pos + 1, selected.size)
            }

            override fun onSuccess(file: File) {
                storage.insert(episode.awemeId, episode.desc, "mix_video", file.absolutePath, file.length())
                io.execute {
                    GallerySaver.save(this@MainActivity, file, true, "${episode.awemeId}.mp4")
                }
                downloadMixEpisode(selected, pos + 1)
            }

            override fun onError(message: String) {
                android.util.Log.d("DouyinMain", "mix download fail: $message")
                // 继续下载下一集
                downloadMixEpisode(selected, pos + 1)
            }
        })
    }

    private fun startDownload() {
        val aweme = currentAweme ?: return
        if (downloading) return
        downloading = true
        btnDownload.isEnabled = false
        progressWrap.visibility = View.VISIBLE
        progressBar.progress = 0
        progressText.text = "准备中…"
        Downloader.resetCancel()

        if (aweme.type == "video") {
            val url = aweme.primaryVideoUrl
            if (url == null) {
                downloading = false
                btnDownload.isEnabled = true
                Toast.makeText(this, "未获取到视频地址", Toast.LENGTH_SHORT).show()
                return
            }
            val target = File(storage.rootDir(), "video/${aweme.awemeId}.mp4")
            Downloader.download(url, target, object : Downloader.Callback {
                override fun onProgress(percent: Int, done: Long, total: Long) {
                    progressBar.progress = percent.coerceAtLeast(0)
                    progressText.text = if (total > 0) "${fmtSize(done)} / ${fmtSize(total)}" else "${fmtSize(done)}"
                }

                override fun onSuccess(file: File) {
                    downloading = false
                    btnDownload.isEnabled = true
                    progressText.text = "完成 ✓"
                    storage.insert(aweme.awemeId, aweme.desc, "video", file.absolutePath, file.length())
                    io.execute {
                        GallerySaver.save(this@MainActivity, file, true, "${aweme.awemeId}.mp4")
                    }
                }

                override fun onError(message: String) {
                    downloading = false
                    btnDownload.isEnabled = true
                    progressWrap.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "下载失败: $message", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            val selected = mutableListOf<Int>()
            selectedImages.forEachIndexed { index, checked -> if (checked) selected.add(index) }
            if (selected.isEmpty()) {
                downloading = false
                btnDownload.isEnabled = true
                progressWrap.visibility = View.GONE
                Toast.makeText(this, "请至少选择一张图片", Toast.LENGTH_SHORT).show()
                return
            }
            downloadingImageCount = selected.size
            downloadedImageCount = 0
            downloadImages(aweme, selected, 0)
        }
    }

    private fun downloadImages(aweme: Aweme, selected: List<Int>, pos: Int) {
        if (pos >= selected.size) {
            downloading = false
            btnDownload.isEnabled = true
            progressText.text = "完成 ✓ 共 ${downloadingImageCount} 张"
            return
        }
        val index = selected[pos]
        val url = aweme.imageUrls[index]
        val target = File(storage.rootDir(), "images/${aweme.awemeId}_${index + 1}.jpg")
        Downloader.download(url, target, object : Downloader.Callback {
            override fun onProgress(percent: Int, done: Long, total: Long) {
                progressText.text = "图集 ${downloadedImageCount}/${downloadingImageCount} · ${fmtSize(done)}"
                val pct = ((downloadedImageCount.toDouble() / downloadingImageCount) * 100).toInt().coerceIn(0, 99)
                progressBar.progress = pct
            }

            override fun onSuccess(file: File) {
                downloadedImageCount++
                storage.insert(aweme.awemeId, aweme.desc, "images", file.absolutePath, file.length())
                io.execute {
                    GallerySaver.save(this@MainActivity, file, false, "${aweme.awemeId}_${index + 1}.jpg")
                }
                downloadImages(aweme, selected, pos + 1)
            }

            override fun onError(message: String) {
                downloading = false
                btnDownload.isEnabled = true
                progressWrap.visibility = View.GONE
                Toast.makeText(this@MainActivity, "图集下载失败: $message", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showSettings() {
        val mixParseStatus = if (storage.isMixParseEnabled()) "开" else "关"
        val items = arrayOf("打开登录页", "清除登录态", "合集解析：$mixParseStatus")
        AlertDialog.Builder(this)
            .setTitle("设置")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openLoginPage()
                    1 -> {
                        CookieStore.clear()
                        Toast.makeText(this, "登录态已清除", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        val newState = !storage.isMixParseEnabled()
                        storage.setMixParseEnabled(newState)
                        Toast.makeText(this, "合集解析已${if (newState) "开启" else "关闭"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

private fun openLoginPage() {
        webView.visibility = View.VISIBLE
        webView.requestFocus()
        CookieStore.saveFromWebView()
        webView.loadUrl("https://www.douyin.com/")
        Toast.makeText(this, "请在页面中完成登录，完成后按返回键收起", Toast.LENGTH_LONG).show()
        startLoginPolling()
    }

    private var loginPollTask: Runnable? = null

    private fun startLoginPolling() {
        stopLoginPolling()
        val task = object : Runnable {
            override fun run() {
                val wasLogged = CookieStore.hasLogin()
                CookieStore.saveFromWebView()
                val nowLogged = CookieStore.hasLogin()
                if (nowLogged && !wasLogged) {
                    Toast.makeText(this@MainActivity, "登录成功，Cookie 已保存", Toast.LENGTH_LONG).show()
                    stopLoginPolling()
                } else if (loginPollTask === this) {
                    webView.postDelayed(this, 2000)
                }
            }
        }
        loginPollTask = task
        webView.postDelayed(task, 2000)
    }

    private fun stopLoginPolling() {
        loginPollTask?.let { webView.removeCallbacks(it) }
        loginPollTask = null
    }

    override fun onBackPressed() {
        if (webView.visibility == View.VISIBLE) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                stopLoginPolling()
                webView.visibility = View.GONE
            }
            return
        }
        super.onBackPressed()
    }

    private fun loadBitmap(url: String, onResult: (Bitmap?) -> Unit) {
        io.execute {
            var bmp: Bitmap? = null
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("Referer", "https://www.douyin.com/")
                conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                )
                conn.inputStream.use { input ->
                    bmp = BitmapFactory.decodeStream(input)
                }
            } catch (_: Exception) {
            }
            runOnUiThread { onResult(bmp) }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmtCount(n: Long): String = when {
        n >= 10000 -> String.format(Locale.getDefault(), "%.1fw", n / 10000.0)
        n >= 1000 -> String.format(Locale.getDefault(), "%.1fk", n / 1000.0)
        else -> n.toString()
    }

    private fun fmtSize(b: Long): String = when {
        b >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1fMB", b / 1048576.0)
        b >= 1024 -> String.format(Locale.getDefault(), "%.1fKB", b / 1024.0)
        else -> "$b B"
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
    }
}