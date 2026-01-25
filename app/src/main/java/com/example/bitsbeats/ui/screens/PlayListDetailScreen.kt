package com.example.bitsbeats.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.TextField
import androidx.compose.ui.unit.dp
import com.example.bitsbeats.R
import com.example.bitsbeats.ui.components.PlaybackController
import com.example.bitsbeats.ui.components.PlaylistStore
import com.example.bitsbeats.ui.components.GenericOptionsSheet
import com.example.bitsbeats.ui.components.GenericOptionItem
import com.example.bitsbeats.util.formatDuration
import androidx.core.net.toUri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// Playlist detail screen: list songs, play entire playlist sequentially, add songs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistName: String,
    onAddSongs: () -> Unit = {},
    // new callback: navigate back to the playlists list directly
    onBackToList: () -> Unit = {}
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(PlaylistStore.getPlaylist(context, playlistName)) }

    // menu state
    // (previous DropdownMenu removed; we now open a bottom sheet)

    var showOptionsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(playlistName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // artwork URI state (so we can update after picking an image)
    var currentArtworkUri by remember {
        mutableStateOf(
            PlaylistStore.getPlaylistImage(
                context,
                playlistName
            )
        )
    }

    // load artwork URI and bitmap
    val artworkBitmap = produceState(
        initialValue = null as androidx.compose.ui.graphics.ImageBitmap?,
        currentArtworkUri
    ) {
        value = null
        if (!currentArtworkUri.isNullOrBlank()) {
            val uriStr = currentArtworkUri
            if (!uriStr.isNullOrBlank()) {
                try {
                    val u = uriStr.toUri()
                    context.contentResolver.openInputStream(u)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        value = bmp?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    value = null
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { /* nothing to release */ }
    }

    fun playIndex(index: Int) {
        if (index < 0 || index >= items.size) return
        try {
            val uris = items.map { it["uri"] as? String ?: "" }.filter { it.isNotBlank() }
            if (uris.isEmpty()) return
            PlaybackController.playQueue(context, uris, index, playlistName = playlistName)
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo reproducir", Toast.LENGTH_SHORT).show()
        }
    }

    // launcher to pick image from device
    val imageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                try {
                    // persist permission
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                PlaylistStore.setPlaylistImage(context, playlistName, it.toString())
                currentArtworkUri = it.toString()
            }
        }

    Column(modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .background(Color(0xFF010000))) {
        TopAppBar(
            title = { Text(text = playlistName, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { onBackToList() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Atrás",
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = { showOptionsSheet = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Opciones",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF010000))
        )

        // Show the bottom sheet with playlist options when requested
        if (showOptionsSheet) {
            GenericOptionsSheet(
                visible = true,
                onDismiss = { showOptionsSheet = false },
                headerContent = null,
                options = listOf(
                    GenericOptionItem(label = "Editar nombre", icon = Icons.Filled.Edit, onClick = {
                        showRenameDialog = true
                    }),
                    GenericOptionItem(label = "Modificar imagen", icon = Icons.Filled.Image, onClick = {
                        imageLauncher.launch(arrayOf("image/*"))
                    }),
                    GenericOptionItem(label = "Eliminar playlist", icon = Icons.Filled.Delete, iconTint = Color(0xFFFF6B6B), onClick = {
                        showDeleteConfirm = true
                    })
                )
            )
        }

        // header artwork
        if (artworkBitmap.value != null) {
            Image(
                bitmap = artworkBitmap.value!!,
                contentDescription = "Artwork",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            // fallback: default drawable
            Image(
                painter = painterResource(id = R.drawable.playlist_default),
                contentDescription = "Artwork default",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop
            )
        }

        // Rename dialog
        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Editar nombre") },
                text = {
                    TextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        placeholder = { Text("Nuevo nombre") })
                },
                confirmButton = {
                    Button(onClick = {
                        val newN = renameText.trim()
                        if (newN.isNotBlank()) {
                            val ok = PlaylistStore.renamePlaylist(context, playlistName, newN)
                            if (ok) {
                                Toast.makeText(context, "Renombrada a '$newN'", Toast.LENGTH_SHORT)
                                    .show()
                                showRenameDialog = false
                                // navigate back to list so user can open the renamed playlist
                                onBackToList()
                            } else {
                                Toast.makeText(
                                    context,
                                    "No se pudo renombrar (ya existe o error)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    Button(onClick = {
                        showRenameDialog = false
                    }) { Text("Cancelar") }
                }
            )
        }

        // Delete confirm dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Eliminar playlist") },
                text = { Text("¿Eliminar la playlist '$playlistName'? Esta acción no se puede deshacer.") },
                confirmButton = {
                    Button(onClick = {
                        try {
                            PlaylistStore.deletePlaylist(context, playlistName)
                            if (PlaybackController.activePlaylistName == playlistName) {
                                PlaybackController.clearPlaybackAndReset()
                            }
                            Toast.makeText(context, "Playlist eliminada", Toast.LENGTH_SHORT).show()
                            showDeleteConfirm = false
                            onBackToList()
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "No se pudo eliminar la playlist",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) { Text("Eliminar") }
                },
                dismissButton = {
                    Button(onClick = {
                        showDeleteConfirm = false
                    }) { Text("Cancelar") }
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            // Play removed from this screen; control playback via mini-player.
            Button(onClick = onAddSongs) { Text("Add songs") }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("No hay canciones en esta playlist", color = Color.White) }
        } else {
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)) {
                items(items.withIndex().toList()) { (idx, item) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable { playIndex(idx) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail image
                        val ctx = LocalContext.current
                        val audioUriString = item["uri"] as? String ?: ""
                        var embeddedBitmap by remember(audioUriString) {
                            mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
                        }
                        LaunchedEffect(audioUriString) {
                            embeddedBitmap = try {
                                com.example.bitsbeats.util.loadEmbeddedArtwork(ctx, audioUriString)
                            } catch (_: Exception) {
                                null
                            }
                        }

                        if (embeddedBitmap != null) {
                            Image(
                                bitmap = embeddedBitmap!!,
                                contentDescription = "Artwork",
                                modifier = Modifier.size(48.dp).clip(CircleShape).clickable { playIndex(idx) }
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.song_default),
                                contentDescription = "Default artwork",
                                modifier = Modifier.size(48.dp).clip(CircleShape).clickable { playIndex(idx) }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item["title"] as? String ?: "Desconocido",
                                color = Color.White
                            )
                            Text(
                                text = item["artist"] as? String ?: "Artista desconocido",
                                color = Color.LightGray
                            )
                        }
                        Text(
                            text = formatDuration((item["duration"] as? Long) ?: 0L),
                            color = Color.LightGray
                        )
                    }
                }
                item{
                    Spacer(modifier = Modifier.height(200.dp))
                }
            }
        }
    }
}
