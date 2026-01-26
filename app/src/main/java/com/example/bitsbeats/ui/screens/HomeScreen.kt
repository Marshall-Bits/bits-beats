package com.example.bitsbeats.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bitsbeats.R
import com.example.bitsbeats.ui.components.PlaylistStore
import com.example.bitsbeats.ui.components.StatsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: () -> Unit,
    onNavigateToFileBrowser: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToPlaylistDetail: (String) -> Unit = {}
) {
    val ctx = LocalContext.current

    // Load last-played playlists from stats and sort by lastPlayed desc
    val stats = remember { StatsStore.exportStats(ctx) }
    val recentPlaylists = remember(stats) {
        try {
            val pls = stats.optJSONObject("playlists")
            val keys = pls?.keys()
            val list = mutableListOf<Pair<String, Long>>()
            if (keys != null) {
                while (keys.hasNext()) {
                    val name = keys.next()
                    val ts = pls.optJSONObject(name)?.optLong("lastPlayed", 0L) ?: 0L
                    if (ts > 0L) list.add(name to ts)
                }
            }
            list.sortedByDescending { it.second }
        } catch (_: Exception) {
            emptyList<Pair<String, Long>>()
        }
    }

    // We'll show up to 4 most recent playlists (2 per column)
    val toShow = remember(recentPlaylists) { recentPlaylists.take(4) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010000)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            // App logo (small) and lowercase name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "App logo",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "bits beats", color = Color.White, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section title
            Text(text = "Last playlists", color = Color.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(start = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // Row with two playlist tiles: image on the left, title on the right for each
            val topTwo = remember(toShow) { toShow.take(2) }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                // First most-recent playlist
                if (topTwo.size > 0) {
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistTile(ctx = ctx, playlistName = topTwo[0].first, onClick = { onNavigateToPlaylistDetail(topTwo[0].first) })
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Second most-recent playlist
                if (topTwo.size > 1) {
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistTile(ctx = ctx, playlistName = topTwo[1].first, onClick = { onNavigateToPlaylistDetail(topTwo[1].first) })
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}


@Composable
private fun PlaylistTile(ctx: android.content.Context, playlistName: String, onClick: () -> Unit) {
    // Compact tile: image on left, title on right
    val imgUri = remember(playlistName) { PlaylistStore.getPlaylistImage(ctx, playlistName) }
    val bitmap = produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, key1 = imgUri) {
        value = null
        if (!imgUri.isNullOrBlank()) {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    try { ctx.contentResolver.openInputStream(imgUri.toUri())?.use { stream -> BitmapFactory.decodeStream(stream) } } catch (_: Exception) { null }
                }
                if (bmp != null) value = bmp.asImageBitmap()
            } catch (_: Exception) { value = null }
        }
    }

    Row(modifier = Modifier
        .fillMaxWidth()
        .height(80.dp)
        .clickable { onClick() }
        .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val imageSize = 72.dp
        if (bitmap.value != null) {
            Image(
                bitmap = bitmap.value!!,
                contentDescription = "Playlist image",
                modifier = Modifier
                    .size(imageSize)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF897DB2)),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.playlist_default),
                contentDescription = "Playlist default",
                modifier = Modifier
                    .size(imageSize)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF897DB2)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = playlistName, color = Color.White)
        }
    }
}