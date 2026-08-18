package com.douyin.crawler

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * 将下载文件写入公共相册目录（MediaStore），使系统相册可见。
 */
object GallerySaver {

    fun save(context: Context, file: File, isVideo: Boolean, displayName: String): Uri? {
        return try {
            val resolver = context.contentResolver
            val mime = if (isVideo) "video/mp4" else "image/jpeg"
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Douyin")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    put(
                        MediaStore.MediaColumns.DATA,
                        File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DCIM
                            ),
                            "Douyin/$displayName"
                        ).absolutePath
                    )
                }
            }
            val uri = resolver.insert(collection, values) ?: return null
            file.inputStream().use { input ->
                resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            null
        }
    }
}