package com.douyin.crawler

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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

    private lateinit var loader: WebViewLoader
    private lateinit var api: ApiClient
    private lateinit var storage: LocalStorage

    private var currentAweme: Aweme? = null
    private var downloading = false
    private var downloadingImageCount = 0
    private var downloadedImageCount = 0
    private val selectedImages = mutableListOf<Boolean>()

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
        resultCard.visibility = View.GONE
        imageStrip.visibility = View.GONE
        selectAllBtn.visibility = View.GONE
        progressWrap.visibility = View.GONE
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
        AlertDialog.Builder(this)
            .setTitle("设置")
            .setItems(arrayOf("打开登录页", "清除登录态")) { _, which ->
                when (which) {
                    0 -> openLoginPage()
                    1 -> {
                        CookieStore.clear()
                        Toast.makeText(this, "登录态已清除", Toast.LENGTH_SHORT).show()
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