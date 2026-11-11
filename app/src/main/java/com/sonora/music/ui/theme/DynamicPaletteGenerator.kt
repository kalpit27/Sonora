package com.sonora.music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.sonora.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class PaletteColors(
    val dominant: Color,
    val vibrant: Color,
    val darkMuted: Color
)

@Composable
fun rememberDynamicPalette(
    song: Song?,
    defaultAccent: Color,
    defaultBackground: Color
): State<PaletteColors> {
    val context = LocalContext.current

    return produceState(
        initialValue = PaletteColors(
            dominant = defaultAccent.copy(alpha = 0.5f),
            vibrant = defaultAccent,
            darkMuted = defaultBackground
        ),
        key1 = song?.id
    ) {
        if (song == null) {
            value = PaletteColors(
                dominant = defaultAccent.copy(alpha = 0.5f),
                vibrant = defaultAccent,
                darkMuted = defaultBackground
            )
            return@produceState
        }

        val bitmap = withContext(Dispatchers.IO) {
            extractArtworkBitmap(context, song.albumArtUri, song.contentUri)
        }

        if (bitmap != null) {
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).resizeBitmapArea(128 * 128).generate()
            }

            val domRgb = palette.dominantSwatch?.rgb
            val vibRgb = palette.vibrantSwatch?.rgb ?: palette.lightVibrantSwatch?.rgb ?: domRgb
            val darkRgb = palette.darkMutedSwatch?.rgb ?: palette.darkVibrantSwatch?.rgb

            val domColor = if (domRgb != null) Color(domRgb) else defaultAccent
            val vibColor = if (vibRgb != null) Color(vibRgb) else defaultAccent
            val darkColor = if (darkRgb != null) Color(darkRgb) else defaultBackground

            value = PaletteColors(
                dominant = domColor,
                vibrant = vibColor,
                darkMuted = darkColor
            )
        } else {
            value = PaletteColors(
                dominant = defaultAccent.copy(alpha = 0.45f),
                vibrant = defaultAccent,
                darkMuted = defaultBackground
            )
        }
    }
}

private fun extractArtworkBitmap(context: Context, artworkUri: Uri?, audioUri: Uri?): Bitmap? {
    try {
        val uriStr = artworkUri?.toString()

        // 1. Web URL (e.g. YouTube thumbnail)
        if (uriStr != null && (uriStr.startsWith("http://") || uriStr.startsWith("https://"))) {
            val conn = (URL(uriStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }
            if (conn.responseCode in 200..299) {
                val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                conn.disconnect()
                if (bmp != null) return bmp
            }
        }

        // 2. Direct file URI
        if (uriStr != null && (uriStr.startsWith("file://") || uriStr.startsWith("/"))) {
            val path = if (uriStr.startsWith("file://")) uriStr.removePrefix("file://") else uriStr
            val file = File(path)
            if (file.exists() && file.length() > 0) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bmp != null) return bmp
            }
        }

        // 3. Android 10+ loadThumbnail from MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (artworkUri != null && artworkUri.scheme == "content") {
                try {
                    val bmp = context.contentResolver.loadThumbnail(artworkUri, Size(128, 128), null)
                    if (bmp != null) return bmp
                } catch (ignored: Exception) {}
            }
            if (audioUri != null && audioUri.scheme == "content") {
                try {
                    val bmp = context.contentResolver.loadThumbnail(audioUri, Size(128, 128), null)
                    if (bmp != null) return bmp
                } catch (ignored: Exception) {}
            }
        }

        // 4. MediaMetadataRetriever embedded picture fallback
        val effectiveUri = audioUri ?: artworkUri
        if (effectiveUri != null) {
            val retriever = MediaMetadataRetriever()
            try {
                if (effectiveUri.scheme == "content") {
                    retriever.setDataSource(context, effectiveUri)
                } else if (!effectiveUri.path.isNullOrBlank()) {
                    retriever.setDataSource(effectiveUri.path)
                }
                val artBytes = retriever.embeddedPicture
                if (artBytes != null && artBytes.isNotEmpty()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
                    if (bmp != null) return bmp
                }
            } catch (ignored: Exception) {
            } finally {
                try { retriever.release() } catch (ignored: Exception) {}
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

