package com.example.bitsbeats.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bitsbeats.ui.components.PlaybackController
import com.example.bitsbeats.util.formatDuration
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen(audioId: Long = -1L, restoreIfNoCurrent: Boolean = true) {
    val context = LocalContext.current

    // When navigated with an audioId play it; if -1 restore last
    LaunchedEffect(audioId) {
        if (audioId != -1L) {
            // explicit selection: play this audio id
            PlaybackController.playAudioId(context, audioId)
        } else {
            // navigation from mini-player or 'open last': if we already have a currentUri active
            // don't re-initialize playback when opening the player. Only attempt restore if nothing loaded.
            if (restoreIfNoCurrent && PlaybackController.currentUri == null) {
                PlaybackController.restoreState(context)
            }
        }
    }

    // Observe controller state
    val title = PlaybackController.title
    val artist = PlaybackController.artist
    val isPlaying = PlaybackController.isPlaying
    val duration = PlaybackController.duration
    val currentPosition = PlaybackController.currentPosition

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010000)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(24.dp)) {
            // Album artwork: use Animatable so rotation preserves position on pause and resets on track change
            val currentUri = PlaybackController.currentUri
            val rotationAnim = remember { Animatable(0f) }

            // Reset rotation when a new track starts (observe both URI and title to be robust)
            LaunchedEffect(currentUri, title) {
                rotationAnim.snapTo(0f)
            }

            // When playing, animate continuously in +360 steps; when paused the coroutine cancels and value is preserved
            LaunchedEffect(isPlaying, currentUri) {
                if (isPlaying) {
                    while (isActive && PlaybackController.isPlaying) {
                        val target = rotationAnim.value + 360f
                        rotationAnim.animateTo(target, animationSpec = tween(durationMillis = 8000, easing = LinearEasing))
                    }
                }
            }

            // Artwork with optional embedded thumbnail overlay
            val ctx = LocalContext.current
            val embeddedBitmapState = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = currentUri) {
                value = try { com.example.bitsbeats.util.loadEmbeddedArtwork(ctx, currentUri) } catch (_: Exception) { null }
            }

            Box(modifier = Modifier.size(200.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = com.example.bitsbeats.R.drawable.song_default),
                    contentDescription = "Album",
                    modifier = Modifier.size(180.dp).rotate(rotationAnim.value % 360f)
                )

                if (embeddedBitmapState.value != null) {
                    Image(
                        bitmap = embeddedBitmapState.value!!,
                        contentDescription = "Embedded artwork",
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .rotate(rotationAnim.value % 360f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (artist.isNotBlank()) Text(text = artist, fontSize = 16.sp, color = Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                val sliderPos = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                Slider(value = sliderPos, onValueChange = { newValue -> PlaybackController.seekTo((newValue * duration).toInt()) }, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.Gray), modifier = Modifier.fillMaxWidth())

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatDuration(currentPosition), fontSize = 12.sp, color = Color.LightGray)
                    Text(text = formatDuration(duration), fontSize = 12.sp, color = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Shuffle on the left
                val shuffleOn = PlaybackController.shuffleEnabled
                val shuffleTint = if (shuffleOn) Color(0xFF1DB954) else Color.White
                IconButton(onClick = { PlaybackController.toggleShuffle() }, modifier = Modifier.size(48.dp)) {
                    Icon(imageVector = Icons.Filled.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(28.dp), tint = shuffleTint)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Center controls
                Row(modifier = Modifier.width(240.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { PlaybackController.prevTrack() }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Canción anterior", modifier = Modifier.size(48.dp), tint = Color.White)
                    }

                    IconButton(onClick = { PlaybackController.togglePlayPause() }, modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White)) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Reproducir", modifier = Modifier.size(48.dp), tint = Color.Black)
                    }

                    IconButton(onClick = { PlaybackController.nextTrack() }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Siguiente canción", modifier = Modifier.size(48.dp), tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Repeat on the right
                val repeatMode = PlaybackController.repeatMode
                val repeatActive = repeatMode != PlaybackController.RepeatMode.OFF
                val repeatIcon = if (repeatMode == PlaybackController.RepeatMode.REPEAT_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
                val repeatTint = if (repeatActive) Color(0xFF1DB954) else Color.White
                IconButton(onClick = { PlaybackController.toggleRepeatMode() }, modifier = Modifier.size(48.dp)) {
                    Icon(imageVector = repeatIcon, contentDescription = "Repeat mode", modifier = Modifier.size(28.dp), tint = repeatTint)
                }
            }
        }
    }
}






















