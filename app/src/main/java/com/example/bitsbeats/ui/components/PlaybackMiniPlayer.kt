package com.example.bitsbeats.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.isActive

@Composable
fun PlaybackMiniPlayer(navController: NavHostController, modifier: Modifier = Modifier) {
    val title = PlaybackController.title
    val artist = PlaybackController.artist
    val isPlaying = PlaybackController.isPlaying

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2D2D2D))
            // navigate to the 'player' route which will NOT reinitialize playback when a track is already loaded
            .clickable { navController.navigate("player") }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // rotate vinyl while playing, preserve rotation when paused using Animatable
        val currentUri = PlaybackController.currentUri
        val rotationAnim = remember { Animatable(0f) }

        // Reset rotation when a new track starts
        LaunchedEffect(currentUri) {
            rotationAnim.snapTo(0f)
        }

        // When playing, keep animating in +360 steps; when paused the coroutine cancels and the value is kept
        LaunchedEffect(isPlaying, currentUri) {
            if (isPlaying) {
                while (isActive && PlaybackController.isPlaying) {
                    val target = rotationAnim.value + 360f
                    rotationAnim.animateTo(target, animationSpec = tween(durationMillis = 8000, easing = LinearEasing))
                }
            }
        }

        Image(
            painter = painterResource(id = com.example.bitsbeats.R.drawable.song_default),
            contentDescription = "Artwork",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .rotate(rotationAnim.value % 360f)
        )

        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text(text = artist.ifBlank { "Artista desconocido" }, color = Color.LightGray, maxLines = 1)
        }
        IconButton(onClick = { PlaybackController.togglePlayPause() }) {
            Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Reproducir", tint = Color.White)
        }
    }
}
