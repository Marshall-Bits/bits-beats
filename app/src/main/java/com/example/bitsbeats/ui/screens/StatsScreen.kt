package com.example.bitsbeats.ui.screens

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
fun StatsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val stats = StatsStore.exportStats(ctx)
    val topSongs = StatsStore.topSongs(ctx, 20)

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
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF010000))
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            item {
                Text("Last played", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                if (lastSongUri.isNotBlank()) {
                    Text("Last song: ${lastSongUri}", color = Color.White)
                    if (lastSongTs > 0) Text("At: ${DateFormat.getDateTimeInstance().format(Date(lastSongTs))}", color = Color.LightGray)
                } else {
                    Text("No song played yet", color = Color.LightGray)
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (lastPlaylistName.isNotBlank()) {
                    Text("Last playlist: ${lastPlaylistName}", color = Color.White)
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
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(text = displayTitle, color = Color.White)
                    if (displayArtist != null) Text(text = displayArtist, color = Color.LightGray)
                    Text(text = "Plays: $count", color = Color.LightGray)
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Top artists", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            items(artists) { (name, count) ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(text = name, color = Color.White)
                    Text(text = "Plays: $count", color = Color.LightGray)
                }
            }
            item{
                Spacer(modifier = Modifier.height(200.dp))
            }
        }
    }
}
