package com.example.bitsbeats.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.bitsbeats.R
import com.example.bitsbeats.ui.components.PlaylistStore
import androidx.core.net.toUri
import kotlin.OptIn

// Playlist list screen: shows all playlists in a 2-column grid. Each card displays artwork and the playlist name.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    onNavigateToPlaylistDetail: (String) -> Unit = {},
    onCreatePlaylist: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var playlists by remember { mutableStateOf(PlaylistStore.loadAll(context).keys.toList()) }

    // create dialog state
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Refresh the list when the screen is shown
    LaunchedEffect(Unit) {
        playlists = PlaylistStore.loadAll(context).keys.toList()
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF010000))) {
        TopAppBar(
            title = { Text(text = "Playlists", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { onNavigateBack() }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Create playlist", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF010000))
        )

        // Create playlist dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create playlist") },
                text = {
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Playlist name") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isBlank()) {
                            Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val ok = try { PlaylistStore.createPlaylist(context, name) } catch (_: Exception) { false }
                        if (ok) {
                            Toast.makeText(context, "Created '$name'", Toast.LENGTH_SHORT).show()
                            showCreateDialog = false
                            newPlaylistName = ""
                            playlists = PlaylistStore.loadAll(context).keys.toList()
                            // notify external handler
                            onCreatePlaylist()
                            // navigate to the details of the newly created playlist
                            onNavigateToPlaylistDetail(name)
                        } else {
                            Toast.makeText(context, "Could not create playlist (already exists)", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Create") }
                },
                dismissButton = {
                    Button(onClick = { showCreateDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No playlists", color = Color.White)
            }
            return@Column
        }

        // Grid of playlist cards (2 columns)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(playlists) { name ->
                // load artwork bitmap if a persisted URI exists
                val artworkUri = PlaylistStore.getPlaylistImage(context, name)
                val artworkBitmap = produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, artworkUri) {
                    value = null
                    if (!artworkUri.isNullOrBlank()) {
                        try {
                            val u = artworkUri.toUri()
                            context.contentResolver.openInputStream(u)?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                value = bmp?.asImageBitmap()
                            }
                        } catch (_: Exception) {
                            value = null
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onNavigateToPlaylistDetail(name) }
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (artworkBitmap.value != null) {
                        Image(
                            bitmap = artworkBitmap.value!!,
                            contentDescription = "Playlist artwork",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.playlist_default),
                            contentDescription = "Playlist artwork default",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    }

                    Text(text = name, color = Color.White, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
