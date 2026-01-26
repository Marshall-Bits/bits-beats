package com.example.bitsbeats.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.get
import com.example.bitsbeats.R
import com.example.bitsbeats.ui.components.PlaylistStore
import androidx.core.net.toUri
import kotlin.OptIn
import androidx.core.graphics.scale

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
                // load artwork android bitmap if a persisted URI exists (for color extraction)
                val artworkAndroid = produceState<Bitmap?>(initialValue = null, key1 = name) {
                    value = null
                    val artworkUri = PlaylistStore.getPlaylistImage(context, name)
                    if (!artworkUri.isNullOrBlank()) {
                        try {
                            val u = artworkUri.toUri()
                            context.contentResolver.openInputStream(u)?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                value = bmp
                            }
                        } catch (_: Exception) {
                            value = null
                        }
                    }
                }

                // ImageBitmap for display
                val artworkBitmap = remember(artworkAndroid.value) { artworkAndroid.value?.asImageBitmap() }

                // Compute dominant color from android bitmap (downscale + average)
                val dominantColorState = produceState(initialValue = Color(0xFF1A001F), artworkAndroid.value) {
                    val default = Color(0xFF1A001F)
                    value = default
                    val bmp = artworkAndroid.value
                    if (bmp != null) {
                        try {
                            val thumb = bmp.scale(40, 40)
                            var rSum = 0L
                            var gSum = 0L
                            var bSum = 0L
                            var count = 0L
                            for (x in 0 until thumb.width) {
                                for (y in 0 until thumb.height) {
                                    val px = thumb[x, y]
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
                                value = Color(colorInt)
                            }
                        } catch (_: Exception) {
                            value = default
                        }
                    }
                }

                val animatedBg by animateColorAsState(targetValue = dominantColorState.value, animationSpec = tween(durationMillis = 400))
                val textColor = if (animatedBg.luminance() > 0.5f) Color.Black else Color.White

                // Fixed-size card with rounded corners and background color from artwork
                Box(modifier = Modifier.padding(8.dp)) {
                    Column(
                        modifier = Modifier
                            .size(width = 150.dp, height = 190.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(animatedBg)
                            .clickable { onNavigateToPlaylistDetail(name) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top image: full width of the card and square, cropped to center
                        if (artworkBitmap != null) {
                            Image(
                                bitmap = artworkBitmap,
                                contentDescription = "Playlist artwork",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.playlist_default),
                                contentDescription = "Playlist artwork default",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            )
                        }

                        // Title area: minimal padding below the image
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = name, color = textColor)
                        }
                    }
                }
            }
        }
    }
}
