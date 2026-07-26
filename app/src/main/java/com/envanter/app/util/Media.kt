package com.envanter.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Kamera ve galeriden gelen görsel/video verisini Gemini'ye gönderilebilir hale getirir:
 * görselleri döndürülmüş + küçültülmüş JPEG'e çevirir, videoları önbelleğe kopyalar.
 */
object Media {

    /** Galeri/kamera URI'sinden döndürülmüş, küçültülmüş JPEG baytları. */
    fun jpegBytes(context: Context, uri: Uri, maxDim: Int = 1280): ByteArray? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return@runCatching null
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > maxDim) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching { ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        compress(applyOrientation(decoded, orientation))
    }.getOrNull()

    /** Dosyadan döndürülmüş, küçültülmüş JPEG baytları. */
    fun jpegBytes(file: File, maxDim: Int = 1280): ByteArray? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return@runCatching null
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > maxDim) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        val orientation = runCatching {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        compress(applyOrientation(decoded, orientation))
    }.getOrNull()

    /** Video gibi büyük içerikleri belleğe almadan önbelleğe kopyalar. */
    fun copyToCache(context: Context, uri: Uri, dirName: String, fileName: String): File? = runCatching {
        val dir = File(context.cacheDir, dirName).apply { mkdirs() }
        val out = File(dir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output, 128 * 1024) }
        } ?: return@runCatching null
        out.takeIf { it.length() > 0 }
    }.getOrNull()

    /**
     * Videonun saniye cinsinden süresi. Gemini token maliyeti süreyle doğru orantılı
     * olduğu için kullanıcıya tahmini tüketimi göstermekte kullanılır; okunamazsa 0.
     */
    fun videoSeconds(file: File): Int = runCatching {
        // MediaMetadataRetriever ancak API 29'dan beri AutoCloseable; minSdk 26 için elle release.
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.path)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ((ms + 999) / 1000).toInt()
        } finally {
            r.release()
        }
    }.getOrDefault(0)

    fun mimeOf(context: Context, uri: Uri, fallback: String = "video/mp4"): String =
        context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() } ?: fallback

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** İnsan okur biçimde boyut (ör. "24,3 MB"). */
    fun humanSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    private fun applyOrientation(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bmp
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated != bmp) bmp.recycle()
            rotated
        }.getOrDefault(bmp)
    }

    private fun compress(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
