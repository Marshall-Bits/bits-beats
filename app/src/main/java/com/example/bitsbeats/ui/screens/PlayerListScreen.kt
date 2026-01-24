package com.example.bitsbeats.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bitsbeats.ui.components.PlaylistStore

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
    var editingName by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        playlists = PlaylistStore.loadAll(context).keys.toList()
    }

    // refresh when returning
    LaunchedEffect(Unit) { /* no-op; UI will update on actions */ }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().background(Color.DarkGray), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Mis Playlists", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(16.dp))

            Button(onClick = { showingDialog = true }, modifier = Modifier.padding(8.dp)) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Nueva playlist", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("New Playlist")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Text(text = "No tienes playlists", color = Color.White)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(playlists) { name ->
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Tappable title -> go to details
                            Row(modifier = Modifier.weight(1f).clickable { onNavigateToPlaylistDetail(name) }, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = name, color = Color.White)
                            }

                            // three-dot menu anchored to the button (menu will appear to the right/below)
                            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                                IconButton(onClick = { menuFor = if (menuFor == name) null else name }) {
                                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Opciones", tint = Color.LightGray)
                                }

                                DropdownMenu(expanded = (menuFor == name), onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(text = { Text("Edit name") }, leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }, onClick = { editingName = name; editText = name; menuFor = null })
                                    DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }, onClick = { playlistToDelete = name; menuFor = null })
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
                        PlaylistStore.deletePlaylist(context, nameToDelete)
                        playlists = PlaylistStore.loadAll(context).keys.toList()
                        playlistToDelete = null
                        Toast.makeText(context, "Playlist eliminada", Toast.LENGTH_SHORT).show()
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
    }
}