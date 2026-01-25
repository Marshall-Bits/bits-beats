package com.example.bitsbeats.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bitsbeats.R
import com.example.bitsbeats.ui.components.GenericOptionItem
import com.example.bitsbeats.ui.components.GenericOptionsSheet
import com.example.bitsbeats.ui.components.PlaylistStore
import com.example.bitsbeats.ui.components.PlaybackController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun PlaylistScreen(onNavigateToPlaylistDetail: (String) -> Unit = {}, onCreatePlaylist: (String) -> Unit = {}) {
    val context = LocalContext.current
    var showingDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var playlists by remember { mutableStateOf<List<String>>(emptyList()) }
    // Name of playlist pending deletion (to show confirm dialog)
    var playlistToDelete by remember { mutableStateOf<String?>(null) }
    // menu state for per-row options
    var menuFor by remember { mutableStateOf<String?>(null) }
    var selectedMenuPlaylist by remember { mutableStateOf<String?>(null) }
    var showPlaylistOptions by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }

    // image picker for modifying playlist image
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            val target = selectedMenuPlaylist
            if (target != null) {
                PlaylistStore.setPlaylistImage(context, target, it.toString())
                playlists = PlaylistStore.loadAll(context).keys.toList()
            }
        }
    }

    LaunchedEffect(Unit) {
        playlists = PlaylistStore.loadAll(context).keys.toList()
    }

    // refresh when returning
    LaunchedEffect(Unit) { /* no-op; UI will update on actions */ }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().background(Color(0xFF010000)), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Mis Playlists", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(16.dp))

            Button(onClick = { showingDialog = true }, modifier = Modifier.padding(8.dp)) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Nueva playlist", modifier = Modifier.size(20.dp), tint = Color.White)
                Spacer(modifier = Modifier.size(8.dp))
                Text("New Playlist")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Text(text = "No tienes playlists", color = Color.White)
            } else {
                // Two-column grid: smaller cards so two fit per row. Add bottom padding to respect mini-player + nav.
                val bottomNavPadding = 180.dp
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = bottomNavPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists) { name ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToPlaylistDetail(name) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E))
                            , horizontalAlignment = Alignment.CenterHorizontally) {
                                // load artwork URI for this playlist; if exists, load bitmap async, otherwise show default
                                val artworkUri = PlaylistStore.getPlaylistImage(context, name)
                                val artworkBitmap = produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, artworkUri) {
                                    value = null
                                    if (!artworkUri.isNullOrBlank()) {
                                        try {
                                            val u = artworkUri.toUri()
                                            context.contentResolver.openInputStream(u)?.use { stream ->
                                                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                                                value = bmp?.asImageBitmap()
                                            }
                                        } catch (_: Exception) { value = null }
                                    }
                                }

                                if (artworkBitmap.value != null) {
                                    Image(
                                        bitmap = artworkBitmap.value!!,
                                        contentDescription = "Playlist artwork",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.playlist_default),
                                        contentDescription = "Playlist artwork",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = name, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)

                                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                                        IconButton(onClick = { selectedMenuPlaylist = name; showPlaylistOptions = true }) {
                                            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Opciones", tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Confirm delete dialog
        if (playlistToDelete != null) {
            val nameToDelete = playlistToDelete!!
            AlertDialog(
                onDismissRequest = { playlistToDelete = null },
                title = { Text("Eliminar playlist") },
                text = { Text("¿Eliminar la playlist '$nameToDelete'? Esta acción no se puede deshacer.") },
                confirmButton = {
                    Button(onClick = {
                        // PlaylistStore.deletePlaylist returns Unit; perform deletion then handle active playlist cleanup.
                        try {
                            PlaylistStore.deletePlaylist(context, nameToDelete)
                            // If the deleted playlist was the active one, clear playback state
                            if (PlaybackController.activePlaylistName == nameToDelete) {
                                PlaybackController.clearPlaybackAndReset()
                            }
                            playlists = PlaylistStore.loadAll(context).keys.toList()
                            playlistToDelete = null
                            Toast.makeText(context, "Playlist eliminada", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se pudo eliminar la playlist", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Eliminar") }
                },
                dismissButton = {
                    Button(onClick = { playlistToDelete = null }) { Text("Cancelar") }
                }
            )
        }

        // Create playlist dialog (restored)
        if (showingDialog) {
            AlertDialog(
                onDismissRequest = { showingDialog = false },
                title = { Text("Nombre de la playlist") },
                text = {
                    TextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("Nombre") })
                },
                confirmButton = {
                    Button(onClick = {
                        if (newName.isNotBlank()) {
                            val ok = PlaylistStore.createPlaylist(context, newName)
                            if (ok) {
                                playlists = PlaylistStore.loadAll(context).keys.toList()
                                showingDialog = false
                                newName = ""
                            } else {
                                Toast.makeText(context, "Ya existe una playlist con ese nombre", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Crear") }
                },
                dismissButton = {
                    Button(onClick = { showingDialog = false }) { Text("Cancelar") }
                }
            )
        }

        // Edit name dialog
        if (editingName != null) {
            val original = editingName!!
            AlertDialog(
                onDismissRequest = { editingName = null },
                title = { Text("Editar nombre") },
                text = {
                    TextField(value = editText, onValueChange = { editText = it }, placeholder = { Text("Nuevo nombre") })
                },
                confirmButton = {
                    Button(onClick = {
                        val newN = editText.trim()
                        if (newN.isNotBlank()) {
                            val ok = PlaylistStore.renamePlaylist(context, original, newN)
                            if (ok) {
                                playlists = PlaylistStore.loadAll(context).keys.toList()
                                editingName = null
                                Toast.makeText(context, "Renombrada a '$newN'", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No se pudo renombrar (ya existe o error)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    Button(onClick = { editingName = null }) { Text("Cancelar") }
                }
            )
        }

        // Generic options sheet for playlist actions (edit name / modify image / delete)
        if (showPlaylistOptions) {
            GenericOptionsSheet(
                visible = showPlaylistOptions,
                onDismiss = { showPlaylistOptions = false; selectedMenuPlaylist = null },
                headerContent = {
                    val p = selectedMenuPlaylist ?: ""
                    val imgUri = PlaylistStore.getPlaylistImage(context, p)
                    val headerBmp = produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, imgUri) {
                        value = null
                        try {
                            if (!imgUri.isNullOrBlank()) {
                                val u = imgUri.toUri()
                                context.contentResolver.openInputStream(u)?.use { stream ->
                                    val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                                    value = bmp?.asImageBitmap()
                                }
                            }
                        } catch (_: Exception) { value = null }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (headerBmp.value != null) {
                            Image(bitmap = headerBmp.value!!, contentDescription = "Playlist image", modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)))
                        } else {
                            Image(painter = painterResource(id = R.drawable.playlist_default), contentDescription = "Playlist image", modifier = Modifier.size(64.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = p, color = Color.White)
                    }
                },
                options = listOf(
                    GenericOptionItem(label = "Editar nombre", icon = Icons.Filled.Edit, onClick = {
                        editingName = selectedMenuPlaylist
                        editText = selectedMenuPlaylist ?: ""
                        showPlaylistOptions = false
                    }),
                    GenericOptionItem(label = "Modificar imagen", icon = Icons.Filled.Image, onClick = {
                        // launch image picker; keep selectedMenuPlaylist set
                        imageLauncher.launch(arrayOf("image/*"))
                        showPlaylistOptions = false
                    }),
                    GenericOptionItem(label = "Eliminar playlist", icon = Icons.Filled.Delete, iconTint = Color(0xFFFF6B6B), onClick = {
                        playlistToDelete = selectedMenuPlaylist
                        showPlaylistOptions = false
                    })
                )
            )
        }
    }
}