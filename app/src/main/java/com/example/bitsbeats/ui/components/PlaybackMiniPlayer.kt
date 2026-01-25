package com.example.bitsbeats.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState

@Composable
fun PlaybackMiniPlayer(navController: NavHostController, modifier: Modifier = Modifier) {
    val title = PlaybackController.title
    val artist = PlaybackController.artist
    val isPlaying = PlaybackController.isPlaying
    val progress = if (PlaybackController.duration > 0) {
        PlaybackController.currentPosition.toFloat() / PlaybackController.duration
    } else {
        0f
    }

    // rotation animation
    val currentUri = PlaybackController.currentUri
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(currentUri) { rotationAnim.snapTo(0f) }

    LaunchedEffect(isPlaying, currentUri) {
        if (isPlaying) {
            while (isActive && PlaybackController.isPlaying) {
                val target = rotationAnim.value + 360f
                rotationAnim.animateTo(target, animationSpec = tween(durationMillis = 8000, easing = LinearEasing))
            }
        }
    }

    // Load embedded artwork asynchronously
    val ctx = LocalContext.current
    val embeddedBitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = currentUri) {
        value = try { com.example.bitsbeats.util.loadEmbeddedArtwork(ctx, currentUri) } catch (_: Exception) { null }
    }

    // Compute representative color (downscale + average) off the UI thread
    val dominantColorState = produceState(initialValue = Color(0xFF1A001F), key1 = embeddedBitmapState.value) {
        val default = Color(0xFF1A001F)
        value = try {
            withContext(Dispatchers.IO) {
                val img = embeddedBitmapState.value
                val bmp = try { img?.asAndroidBitmap() } catch (_: Exception) { null }
                if (bmp != null) {
                    try {
                        val thumb = android.graphics.Bitmap.createScaledBitmap(bmp, 40, 40, true)
                        var rSum = 0L
                        var gSum = 0L
                        var bSum = 0L
                        var count = 0L
                        for (x in 0 until thumb.width) {
                            for (y in 0 until thumb.height) {
                                val px = thumb.getPixel(x, y)
                                val a = android.graphics.Color.alpha(px)
                                if (a < 20) continue
                                rSum += android.graphics.Color.red(px)
                                gSum += android.graphics.Color.green(px)
                                bSum += android.graphics.Color.blue(px)
                                count++
                            }
                        }
                        if (count > 0) {
                            val rAvg = (rSum / count).toInt().coerceIn(0, 255)
                            val gAvg = (gSum / count).toInt().coerceIn(0, 255)
                            val bAvg = (bSum / count).toInt().coerceIn(0, 255)
                            val colorInt = (0xFF shl 24) or (rAvg shl 16) or (gAvg shl 8) or bAvg
                            Color(colorInt)
                        } else default
                    } catch (_: Exception) { default }
                } else default
            }
        } catch (_: Exception) { default }
    }

    val animatedBg by animateColorAsState(targetValue = dominantColorState.value, animationSpec = tween(durationMillis = 400))
    val textColor = if (animatedBg.luminance() > 0.5f) Color.Black else Color.White

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(animatedBg)
            .clickable { navController.navigate("player") }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = com.example.bitsbeats.R.drawable.song_default),
                    contentDescription = "Artwork",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .rotate(rotationAnim.value % 360f)
                )

                if (embeddedBitmapState.value != null) {
                    Image(
                        bitmap = embeddedBitmapState.value!!,
                        contentDescription = "Embedded artwork",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .rotate(rotationAnim.value % 360f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1)
                Text(text = artist.ifBlank { "Artista desconocido" }, color = textColor.copy(alpha = 0.8f), maxLines = 1)
            }
            IconButton(onClick = { PlaybackController.togglePlayPause() }) {
                Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Reproducir", tint = textColor)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(textColor.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .height(2.dp)
                    .background(textColor)
            )
        }
    }
}
