package com.soundbox.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * 极简封面加载器。先试 artworkUri（MediaStore 专辑封面 / 已落盘的内嵌封面），
 * 失败再直接从音频文件里抠内嵌图。带内存缓存与失败记忆，避免反复解析。
 */
object ArtworkLoader {

    private const val TARGET_PX = 320

    private val cache = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024L).toInt() / 8).coerceAtLeast(8 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val misses: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun peek(track: Track?): Bitmap? = track?.let { cache.get(keyOf(it)) }

    suspend fun load(context: Context, track: Track): Bitmap? = withContext(Dispatchers.IO) {
        val key = keyOf(track)
        cache.get(key)?.let { return@withContext it }
        if (key in misses) return@withContext null

        val bitmap = track.artworkUri?.let { decodeScaled(context, it) }
            ?: decodeEmbedded(context, track.uri)

        if (bitmap != null) cache.put(key, bitmap) else misses.add(key)
        bitmap
    }

    fun clear() {
        cache.evictAll()
        misses.clear()
    }

    private fun keyOf(track: Track): String = track.artworkUri?.toString() ?: track.id

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()

    private fun decodeEmbedded(context: Context, uri: Uri): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val bytes = mmr.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var shortest = minOf(width, height)
        while (shortest / 2 >= TARGET_PX) {
            shortest /= 2
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}
