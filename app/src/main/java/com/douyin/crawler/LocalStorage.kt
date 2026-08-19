package com.douyin.crawler

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.MediaScannerConnection
import java.io.File

class LocalStorage(context: Context) : SQLiteOpenHelper(context, "douyin.db", null, 1) {

    private val appContext: Context = context.applicationContext
    private val prefs = context.getSharedPreferences("douyin_settings", Context.MODE_PRIVATE)

    data class Record(
        val awemeId: String,
        val desc: String,
        val type: String,
        val filePath: String,
        val size: Long,
        val createdAt: Long
    )

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            "CREATE TABLE IF NOT EXISTS history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "aweme_id TEXT," +
                "desc TEXT," +
                "type TEXT," +
                "file_path TEXT," +
                "size INTEGER," +
                "created_at INTEGER)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    // 合集解析开关
    fun isMixParseEnabled(): Boolean {
        return prefs.getBoolean("enable_mix_parse", true)
    }

    fun setMixParseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enable_mix_parse", enabled).apply()
    }

    fun rootDir(): File {
        val dir = File(appContext.getExternalFilesDir(null), "Douyin")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun insert(awemeId: String, desc: String, type: String, filePath: String, size: Long) {
        val cv = ContentValues().apply {
            put("aweme_id", awemeId)
            put("desc", desc)
            put("type", type)
            put("file_path", filePath)
            put("size", size)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("history", null, cv)
        MediaScannerConnection.scanFile(appContext, arrayOf(filePath), null, null)
    }

    fun all(): List<Record> {
        val out = ArrayList<Record>()
        readableDatabase.rawQuery(
            "SELECT * FROM history ORDER BY created_at DESC LIMIT 50", null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Record(
                        c.getString(c.getColumnIndexOrThrow("aweme_id")),
                        c.getString(c.getColumnIndexOrThrow("desc")),
                        c.getString(c.getColumnIndexOrThrow("type")),
                        c.getString(c.getColumnIndexOrThrow("file_path")),
                        c.getLong(c.getColumnIndexOrThrow("size")),
                        c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return out
    }

    fun clear() {
        writableDatabase.delete("history", null, null)
    }
}