package com.pocket.opencode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Вложения изображений: content:// из галереи копируется в кэш приложения,
 * уменьшается и пережимается в JPEG. Сервер читает `file://` из того же
 * uid, поэтому исходный URI может исчезнуть после закрытия пикера.
 */
object AttachmentStore {
    private const val MAX_SIDE = 1600
    private const val QUALITY = 88
    private const val MAX_FILES = 6
    private const val KEEP_MS = 3L * 24 * 60 * 60 * 1000

    fun dir(paths: ServerPaths): File = File(paths.cache, "attachments")

    fun import(context: Context, paths: ServerPaths, uri: Uri): Attachment? {
        val dir = dir(paths)
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return null
        cleanup(dir)
        val name = displayName(context, uri) ?: "image"
        val bitmap = readScaled(context, uri) ?: return null
        val target = File(dir, "img_${UUID.randomUUID()}.jpg")
        return runCatching {
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            Attachment(
                uri = "file://${target.absolutePath}",
                name = name.substringBeforeLast('.').ifBlank { "image" } + ".jpg",
                mime = "image/jpeg",
            )
        }.getOrNull().also {
            bitmap.recycle()
            if (it == null) target.delete()
        }
    }

    /** Достаёт Bitmap из file:// или data:image/...;base64 для показа в ленте. */
    fun decode(context: Context, uri: String, maxSide: Int = 1200): Bitmap? {
        if (uri.startsWith("data:")) {
            val payload = uri.substringAfter(',', "")
            if (payload.isBlank()) return null
            val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: return null
            return decodeScaled(bytes, maxSide)
        }
        val path = uri.removePrefix("file://")
        if (path.isBlank()) return null
        return runCatching {
            val file = File(path)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    private fun readScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
        }
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull() ?: return null
        val oriented = orient(context, uri, bitmap)
        return scaleToMax(oriented, MAX_SIDE)
    }

    private fun decodeScaled(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width, height) / sample > maxSide * 2) sample *= 2
        return sample
    }

    private fun orient(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (rotation == 0f) return bitmap
        return runCatching {
            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }

    private fun scaleToMax(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val factor = maxSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).toInt().coerceAtLeast(1),
            (bitmap.height * factor).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun cleanup(dir: File) {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        runCatching {
            val files = dir.listFiles()?.filter { it.isFile }.orEmpty().sortedByDescending {
                it.lastModified()
            }
            files.forEachIndexed { index, file ->
                if (index >= MAX_FILES || file.lastModified() < cutoff) file.delete()
            }
        }
    }
}
