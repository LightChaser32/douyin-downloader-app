package com.douyin.crawler

import android.content.Context
import android.webkit.CookieManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object CookieStore {

    private const val PREF = "douyin_cookies"
    private const val KEY_COOKIE = "cookie_string"
    private const val KEY_LOGIN = "has_login"
    private lateinit var prefs: android.content.SharedPreferences
    private val lock = ReentrantLock()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    private fun savedCookieString(): String {
        lock.withLock {
            return prefs.getString(KEY_COOKIE, "") ?: ""
        }
    }

    fun saveFromWebView() {
        lock.withLock {
            val cm = CookieManager.getInstance()
            val domains = listOf("https://www.douyin.com/", "https://www.iesdouyin.com/")
            val parts = LinkedHashSet<String>()
            domains.forEach { d ->
                val c = cm.getCookie(d) ?: return@forEach
                c.split(";").forEach { kv ->
                    val t = kv.trim()
                    if (t.isNotEmpty()) parts.add(t)
                }
            }
            val joined = parts.joinToString("; ")
            prefs.edit().putString(KEY_COOKIE, joined).apply()
            // 是否包含登录态
            val hasLogin = parts.any { it.startsWith("sessionid=") && it.length > "sessionid=".length }
            prefs.edit().putBoolean(KEY_LOGIN, hasLogin).apply()
        }
    }

    fun cookieString(): String = savedCookieString()

    fun hasLogin(): Boolean = lock.withLock { prefs.getBoolean(KEY_LOGIN, false) }

    fun clear() {
        lock.withLock {
            prefs.edit().clear().apply()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }
}