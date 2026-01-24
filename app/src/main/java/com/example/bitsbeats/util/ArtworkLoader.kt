package com.example.bitsbeats.util

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun loadEmbeddedArtwork(context: Context, uriString: String?): ImageBitmap? {
    if (uriString.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(uriString)
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            val fd = pfd?.fileDescriptor ?: return@withContext null
            retriever.setDataSource(fd)
            val art = retriever.embeddedPicture ?: return@withContext null
            val bmp = BitmapFactory.decodeByteArray(art, 0, art.size) ?: return@withContext null
            bmp.asImageBitmap()
        } catch (e: Exception) {
            null
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
