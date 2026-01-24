package com.example.bitsbeats.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

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
        Icon(imageVector = Icons.Filled.Album, contentDescription = "Artwork", tint = Color.White, modifier = Modifier.size(48.dp))
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
