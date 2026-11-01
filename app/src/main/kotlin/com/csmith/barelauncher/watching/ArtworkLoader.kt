package com.csmith.barelauncher.watching

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.ByteBuffer

private const val TAG = "ArtworkLoader"

/** Fetches artwork that an app shared as a link instead of as an image. */
class ArtworkLoader(private val context: Context, private val http: OkHttpClient) {
    /** Null when the link can't be read, e.g. it's private to the app that shared it. */
    suspend fun load(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ImageDecoder.decodeBitmap(source(uri)) { decoder, info, _ ->
                // In ordinary memory rather than on the GPU, so it can be saved to disk.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize((info.size.width / ARTWORK_WIDTH).coerceAtLeast(1))
            }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't load artwork from $uri", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to read artwork at $uri", e)
            null
        }
    }

    private fun source(uri: Uri): ImageDecoder.Source = when (uri.scheme) {
        "http", "https" -> ImageDecoder.createSource(ByteBuffer.wrap(download(uri)))
        // content://, android.resource:// and file:// links.
        else -> ImageDecoder.createSource(context.contentResolver, uri)
    }

    private fun download(uri: Uri): ByteArray =
        http.newCall(Request.Builder().url(uri.toString()).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.bytes()
        }
}
