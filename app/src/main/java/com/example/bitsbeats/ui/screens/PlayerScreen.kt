package com.example.bitsbeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bitsbeats.ui.components.PlaybackController
import com.example.bitsbeats.formatDuration

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
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(24.dp)) {
            Box(modifier = Modifier.size(200.dp).clip(CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Filled.Album, contentDescription = "Album", modifier = Modifier.size(180.dp), tint = Color.White)
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

            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                // Previous track
                IconButton(onClick = { PlaybackController.prevTrack() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Canción anterior", modifier = Modifier.size(48.dp), tint = Color.White)
                }

                // Play / Pause
                IconButton(onClick = { PlaybackController.togglePlayPause() }, modifier = Modifier.size(80.dp)) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Reproducir", modifier = Modifier.size(64.dp), tint = Color.White)
                }

                // Next track
                IconButton(onClick = { PlaybackController.nextTrack() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Siguiente canción", modifier = Modifier.size(48.dp), tint = Color.White)
                }
            }
        }
    }
}
