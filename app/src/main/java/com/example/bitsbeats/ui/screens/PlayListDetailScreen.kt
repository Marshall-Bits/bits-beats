package com.example.bitsbeats.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bitsbeats.ui.components.PlaybackController
import com.example.bitsbeats.ui.components.PlaylistStore
import com.example.bitsbeats.util.formatDuration

// Playlist detail screen: list songs, play entire playlist sequentially, add songs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(playlistName: String, onBack: () -> Unit = {}, onAddSongs: () -> Unit = {}) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(PlaylistStore.getPlaylist(context, playlistName)) }

    // Observe controller state
    val playbackIsPlaying = PlaybackController.isPlaying

    DisposableEffect(Unit) {
        onDispose { /* nothing to release */ }
    }

    fun playIndex(index: Int) {
        if (index < 0 || index >= items.size) return
        try {
            val uris = items.map { it["uri"] as? String ?: "" }.filter { it.isNotBlank() }
            if (uris.isEmpty()) return
            PlaybackController.playQueue(context, uris, index)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo reproducir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().background(Color.DarkGray)) {
        TopAppBar(title = { Text(text = playlistName, color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2D2D2D)))

        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                if (items.isNotEmpty()) { playIndex(0) }
                else PlaybackController.togglePlayPause()
            }, enabled = items.isNotEmpty()) {
                Text(if (playbackIsPlaying) "PAUSE" else "PLAY")
            }
            Button(onClick = onAddSongs) { Text("Add songs") }
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay canciones en esta playlist", color = Color.White) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(items.withIndex().toList()) { (idx, item) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { playIndex(idx) }, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item["title"] as? String ?: "Desconocido", color = Color.White)
                            Text(text = item["artist"] as? String ?: "Artista desconocido", color = Color.LightGray)
                        }
                        Text(text = formatDuration((item["duration"] as? Long) ?: 0L), color = Color.LightGray)
                    }
                }
            }
        }
    }
}