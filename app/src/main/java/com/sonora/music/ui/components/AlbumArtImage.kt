package com.sonora.music.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sonora.music.ui.theme.ElectricOrange
import com.sonora.music.ui.theme.SpotifyBlack
import com.sonora.music.ui.theme.SpotifyCardElevated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Global in-memory LRU cache for resolved album artwork (Bitmaps and verified URIs).
 * Prevents re-extracting ID3 tags or reloading artwork across 400ms audio player ticks.
 */
internal object ArtworkMemoryCache {
    private val lruCache = object : android.util.LruCache<String, Any>(128) {}

    fun get(key: String): Any? = synchronized(lruCache) { lruCache.get(key) }
    fun put(key: String, value: Any) { synchronized(lruCache) { lruCache.put(key, value) } }
}

@Composable
fun AlbumArtImage(
    artworkUri: Any?,
    modifier: Modifier = Modifier,
    audioUri: Any? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    contentDescription: String? = "Album Artwork"
) {
    val context = LocalContext.current
    val uriString = artworkUri?.toString()
    val audioUriString = audioUri?.toString()
    val cacheKey = remember(uriString, audioUriString) { "${uriString}_$audioUriString" }

    // Instant initial value from memory cache or direct web/file URI to completely eliminate placeholder flicker
    val initialModel = remember(cacheKey) {
        ArtworkMemoryCache.get(cacheKey) ?: run {
            if (uriString != null && (uriString.startsWith("http://") || uriString.startsWith("https://") ||
                uriString.startsWith("file://") || (uriString.startsWith("/") && !uriString.contains("albumart")))) {
                uriString
            } else null
        }
    }

    // Resolve artwork asynchronously if not already cached
    val resolvedModel by produceState<Any?>(initialValue = initialModel, key1 = cacheKey) {
        val cached = ArtworkMemoryCache.get(cacheKey)
        if (cached != null) {
            value = cached
            return@produceState
        }

        // 1. Direct Web URLs (YouTube thumbnails, HTTP images)
        if (uriString != null) {
            if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                if (uriString.contains("maxresdefault.jpg")) {
                    withContext(Dispatchers.IO) {
                        try {
                            val conn = (java.net.URL(uriString).openConnection() as java.net.HttpURLConnection).apply {
                                requestMethod = "HEAD"
                                connectTimeout = 2500
                                readTimeout = 2500
                            }
                            val finalUrl = if (conn.responseCode in 200..299) {
                                uriString
                            } else {
                                uriString.replace("maxresdefault.jpg", "mqdefault.jpg")
                            }
                            conn.disconnect()
                            ArtworkMemoryCache.put(cacheKey, finalUrl)
                            value = finalUrl
                        } catch (e: Exception) {
                            val fallback = uriString.replace("maxresdefault.jpg", "mqdefault.jpg")
                            ArtworkMemoryCache.put(cacheKey, fallback)
                            value = fallback
                        }
                    }
                    return@produceState
                }
                ArtworkMemoryCache.put(cacheKey, uriString)
                value = uriString
                return@produceState
            }

            // 2. Direct local image file paths
            if ((uriString.startsWith("file://") || uriString.startsWith("/")) &&
                (uriString.endsWith(".jpg", true) || uriString.endsWith(".jpeg", true) ||
                 uriString.endsWith(".png", true) || uriString.endsWith(".webp", true))
            ) {
                ArtworkMemoryCache.put(cacheKey, uriString)
                value = uriString
                return@produceState
            }
        }

        withContext(Dispatchers.IO) {
            // 3. Android Content URIs (MediaStore album art or Android 10+ Albums)
            if (uriString != null && uriString.startsWith("content://")) {
                val parsedArtworkUri = if (artworkUri is Uri) artworkUri else Uri.parse(uriString)

                if (uriString.contains("albumart")) {
                    val albumId = uriString.substringAfterLast("/").toLongOrNull()
                    if (albumId != null) {
                        try {
                            val albumContentUri = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                                albumId
                            )
                            val bitmap = context.contentResolver.loadThumbnail(albumContentUri, Size(512, 512), null)
                            if (bitmap != null) {
                                ArtworkMemoryCache.put(cacheKey, bitmap)
                                value = bitmap
                                return@withContext
                            }
                        } catch (ignored: Exception) {}
                    }
                }

                try {
                    val bitmap = context.contentResolver.loadThumbnail(parsedArtworkUri, Size(512, 512), null)
                    if (bitmap != null) {
                        ArtworkMemoryCache.put(cacheKey, bitmap)
                        value = bitmap
                        return@withContext
                    }
                } catch (ignored: Exception) {}
            }

            // 4. Retrieve embedded artwork from the audio file itself (audioUri or effective Uri)
            val effectiveAudioUri = audioUri ?: (if (uriString != null && !uriString.contains("albumart")) artworkUri else null)
            if (effectiveAudioUri != null) {
                val parsedAudioUri = when (effectiveAudioUri) {
                    is Uri -> effectiveAudioUri
                    is String -> Uri.parse(effectiveAudioUri)
                    else -> null
                }

                if (parsedAudioUri != null) {
                    if (parsedAudioUri.scheme == "content") {
                        try {
                            val bitmap = context.contentResolver.loadThumbnail(parsedAudioUri, Size(512, 512), null)
                            if (bitmap != null) {
                                ArtworkMemoryCache.put(cacheKey, bitmap)
                                value = bitmap
                                return@withContext
                            }
                        } catch (ignored: Exception) {}
                    }

                    try {
                        val mmr = MediaMetadataRetriever()
                        if (parsedAudioUri.scheme == "content") {
                            mmr.setDataSource(context, parsedAudioUri)
                        } else {
                            val path = parsedAudioUri.path ?: parsedAudioUri.toString()
                            mmr.setDataSource(path)
                        }
                        val bytes = mmr.embeddedPicture
                        mmr.release()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (decoded != null) {
                                ArtworkMemoryCache.put(cacheKey, decoded)
                                value = decoded
                                return@withContext
                            }
                        }
                    } catch (ignored: Exception) {}
                }
            }

            // 5. Fallback
            if (artworkUri != null) {
                ArtworkMemoryCache.put(cacheKey, artworkUri)
                value = artworkUri
            } else {
                value = null
            }
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(SpotifyCardElevated),
        contentAlignment = Alignment.Center
    ) {
        // Persistent placeholder rendered underneath so no empty frames flash
        SonoraArtworkPlaceholder()

        if (resolvedModel != null) {
            val request = remember(resolvedModel) {
                ImageRequest.Builder(context)
                    .data(resolvedModel)
                    .crossfade(false)
                    .transformations(RemoveLetterboxTransformation())
                    .build()
            }

            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Crops out black letterbox bars (such as YouTube 480x360 hqdefault padding)
 * to ensure images completely fill cards from edge-to-edge.
 */
class RemoveLetterboxTransformation : coil.transform.Transformation {
    override val cacheKey: String = "RemoveLetterboxTransformation"

    override suspend fun transform(input: android.graphics.Bitmap, size: coil.size.Size): android.graphics.Bitmap {
        val width = input.width
        val height = input.height
        if (width <= 0 || height <= 0) return input

        val ratio = width.toFloat() / height.toFloat()
        // Standard 4:3 images with 16:9 content embedded (ratio ~ 1.33) have 12.5% black letterboxing at top and bottom
        if (ratio in 1.25f..1.42f) {
            val topOffset = (height * 0.125f).toInt()
            val croppedHeight = height - (2 * topOffset)
            if (croppedHeight > 0) {
                return android.graphics.Bitmap.createBitmap(input, 0, topOffset, width, croppedHeight)
            }
        }
        return input
    }
}

/**
 * Aesthetic, stylized music artwork placeholder with warm acoustic gradient
 * and concentric vinyl groove rings so the card NEVER looks empty.
 */
@Composable
fun SonoraArtworkPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF3B1E08),
                        Color(0xFF241407),
                        SpotifyBlack
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Concentric Vinyl Rings (aesthetic texture)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = maxR * 0.85f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = maxR * 0.65f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = maxR * 0.45f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Center Acoustic Emblem
        Box(
            modifier = Modifier
                .fillMaxSize(0.42f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ElectricOrange.copy(alpha = 0.9f),
                            Color(0xFF8A3400)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.fillMaxSize(0.55f)
            )
        }
    }
}
