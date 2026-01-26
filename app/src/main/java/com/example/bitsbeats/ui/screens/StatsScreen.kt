package com.example.bitsbeats.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bitsbeats.ui.components.StatsStore
import java.text.DateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onNavigateBack: () -> Unit) {
    val ctx = LocalContext.current
    val stats = StatsStore.exportStats(ctx)
    val topSongs = StatsStore.topSongs(ctx, 5)

    @SuppressLint("DefaultLocale")
    fun formatMs(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val hours = ms / 3600000L
        val minutes = (ms % 3600000L) / 60000L
        val seconds = (ms % 60000L) / 1000L
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%d:%02d", minutes, seconds)
    }

    // extract artists map from export
    val artists = try {
        val a = stats.optJSONObject("artists")
        val keys = a?.keys()
        val list = mutableListOf<Pair<String, Int>>()
        if (keys != null) {
            while (keys.hasNext()) {
                val k = keys.next()
                list.add(k to (a.optInt(k, 0)))
            }
        }
        list.sortedByDescending { it.second }
    } catch (_: Exception) { emptyList<Pair<String, Int>>() }

    val lastSongUri = stats.optString("lastSongUri", "")
    val lastSongTs = stats.optLong("lastSongTs", 0L)
    val lastPlaylistName = stats.optString("lastPlaylistName", "")
    val lastPlaylistTs = stats.optLong("lastPlaylistTs", 0L)

    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF010000))
        .padding(8.dp)) {
        TopAppBar(
            title = { Text("Statistics", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF010000))
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            item {
                Text("Last played", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                if (lastSongUri.isNotBlank()) {
                    val songObj = stats.optJSONObject("songs")?.optJSONObject(lastSongUri)
                    val displayTitle = songObj?.optString("title")?.takeIf { it.isNotBlank() } ?: lastSongUri
                    val displayArtist = songObj?.optString("artist")?.takeIf { it.isNotBlank() }
                    Text("Last song: $displayTitle", color = Color.White)
                    if (!displayArtist.isNullOrBlank()) Text("Artist: $displayArtist", color = Color.LightGray)
                    val songMs = try { StatsStore.getSongPlayedMs(ctx, lastSongUri) } catch (_: Exception) { 0L }
                    if (songMs > 0L) Text("Total time: ${formatMs(songMs)}", color = Color.LightGray)
                    if (lastSongTs > 0) Text("At: ${DateFormat.getDateTimeInstance().format(Date(lastSongTs))}", color = Color.LightGray)
                } else {
                    Text("No song played yet", color = Color.LightGray)
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (lastPlaylistName.isNotBlank()) {
                    Text("Last playlist: ${lastPlaylistName}", color = Color.White)
                    val plMs = try { StatsStore.getPlaylistPlayedMs(ctx, lastPlaylistName) } catch (_: Exception) { 0L }
                    if (plMs > 0L) Text("Total time: ${formatMs(plMs)}", color = Color.LightGray)
                    if (lastPlaylistTs > 0) Text("At: ${DateFormat.getDateTimeInstance().format(Date(lastPlaylistTs))}", color = Color.LightGray)
                } else {
                    Text("No playlist played yet", color = Color.LightGray)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Top songs", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            items(topSongs) { (uri, count) ->
                val songObj = stats.optJSONObject("songs")?.optJSONObject(uri)
                val displayTitle = songObj?.optString("title")?.takeIf { it.isNotBlank() } ?: uri
                val displayArtist = songObj?.optString("artist")?.takeIf { it.isNotBlank() }
                val playedMs = try { StatsStore.getSongPlayedMs(ctx, uri) } catch (_: Exception) { 0L }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(text = displayTitle, color = Color.White)
                    if (displayArtist != null) Text(text = displayArtist, color = Color.LightGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Plays: $count", color = Color.LightGray)
                        if (playedMs > 0L) Text(text = "Time: ${formatMs(playedMs)}", color = Color.LightGray)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Top artists", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            val artistsPlayedObj = stats.optJSONObject("artistsPlayed")
            items(artists) { (name, count) ->
                val artistMs = artistsPlayedObj?.optLong(name, 0L) ?: 0L
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(text = name, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Plays: $count", color = Color.LightGray)
                        if (artistMs > 0L) Text(text = "Time: ${formatMs(artistMs)}", color = Color.LightGray)
                    }
                }
            }
            item{
                Spacer(modifier = Modifier.height(200.dp))
            }
        }
    }
}
