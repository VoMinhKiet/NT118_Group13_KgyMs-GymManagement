package com.kgyms.app.ui.member

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlin.math.max

internal const val MEMBER_AVATAR_SIZE = 384

internal fun memberInitials(name: String): String = name.trim().split(Regex("\\s+"))
    .filter { it.isNotEmpty() }.takeLast(2).joinToString("") { it.take(1).uppercase(java.util.Locale.ROOT) }

internal fun avatarSampleSize(width: Int, height: Int, target: Int = MEMBER_AVATAR_SIZE): Int {
    require(width > 0 && height > 0 && target > 0)
    var sample = 1
    while (max(width, height) / sample > target * 2L) sample *= 2
    return sample
}

/** Blocking: callers must use Dispatchers.IO. No original photo is copied to storage. */
internal fun decodeMemberAvatar(context: Context, uri: Uri): Bitmap? = try {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val size = info.size
            val scale = minOf(1.0, MEMBER_AVATAR_SIZE.toDouble() / max(size.width, size.height))
            decoder.setTargetSize(max(1, (size.width * scale).toInt()), max(1, (size.height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = avatarSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        decoded?.let { source ->
            val orientation = runCatching { resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            val scale = minOf(1.0, MEMBER_AVATAR_SIZE.toDouble() / max(oriented.width, oriented.height))
            val result = Bitmap.createScaledBitmap(oriented, max(1, (oriented.width * scale).toInt()),
                max(1, (oriented.height * scale).toInt()), true)
            if (source !== oriented && source !== result) source.recycle()
            if (oriented !== result) oriented.recycle()
            result
        }
    }
} catch (_: Exception) { null }
