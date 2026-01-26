package com.example.bitsbeats.ui.screens

import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bitsbeats.data.FileItem
import com.example.bitsbeats.data.FileRepository
import com.example.bitsbeats.data.MediaRepository
import com.example.bitsbeats.data.AudioFile
import com.example.bitsbeats.ui.components.GenericOptionsSheet
import com.example.bitsbeats.ui.components.GenericOptionItem
import com.example.bitsbeats.ui.components.PlaylistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onFileSelected: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    addToPlaylistName: String? = null
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val mediaResults = remember { mutableStateListOf<AudioFile>() }
    val fileResults = remember { mutableStateListOf<FileItem>() }
    val pathToId = remember { mutableStateMapOf<String, Long?>() }
    var loading by remember { mutableStateOf(false) }

    // Options sheet state (reused from FileBrowserScreen pattern)
    var showOptionsMenu by remember { mutableStateOf(false) }
    var selectedAudioUri by remember { mutableStateOf<String?>(null) }
    var selectedAudioTitle by remember { mutableStateOf("") }
    var selectedAudioArtist by remember { mutableStateOf("") }
    var selectedAudioDuration by remember { mutableStateOf(0L) }
    var playlists by remember { mutableStateOf<List<String>>(emptyList()) }

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistForSingleDialog by remember { mutableStateOf(false) }
    var newPlaylistNameForSingle by remember { mutableStateOf("") }

    // Permission check (reuse same permission as FileBrowserScreen)
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    val hasPermission = ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // Search job cancellation
    val scope = rememberCoroutineScope()
    var searchJob: Job? = remember { null }

    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
    }

    fun startSearch(q: String) {
        cancelSearch()
        if (q.isBlank()) {
            mediaResults.clear()
            fileResults.clear()
            return
        }

        loading = true
        searchJob = scope.launch {
            try {
                // fast: MediaStore
                val m = withContext(Dispatchers.IO) { MediaRepository.searchByTitleOrArtist(context.contentResolver, q, limit = 200) }
                mediaResults.clear()
                mediaResults.addAll(m)

                // populate pathToId map for media results
                pathToId.clear()
                m.forEach { pathToId["media:${it.id}"] = it.id }

                // slower: filesystem scan with full metadata reads
                val roots = mutableListOf<String>()
                try {
                    val primary = Environment.getExternalStorageDirectory().absolutePath
                    roots.add(primary)
                    context.getExternalFilesDirs(null)?.forEach { d ->
                        if (d == null) return@forEach
                        val root = d.absolutePath.substringBefore("/Android")
                        if (root.isNotBlank() && !roots.contains(root)) roots.add(root)
                    }
                } catch (_: Exception) {}

                val files = withContext(Dispatchers.IO) { FileRepository.searchFilesWithMetadata(roots, q, maxResults = 200, maxDepth = 5) }

                // Attempt to resolve media ids for file results
                withContext(Dispatchers.IO) {
                    files.forEach { fi ->
                        try {
                            val id = MediaRepository.queryAudioIdFromPath(context.contentResolver, fi.path)
                            pathToId[fi.path] = id
                        } catch (_: Exception) {
                            pathToId[fi.path] = null
                        }
                    }
                }

                // Merge: mediaResults first; then fileResults (exclude those already in media by path or id)
                fileResults.clear()
                val seenPaths = mutableSetOf<String>()
                val seenIds = mediaResults.map { it.id }.toMutableSet()
                files.forEach { fi ->
                    val id = pathToId[fi.path]
                    if (id != null && seenIds.contains(id)) return@forEach
                    if (seenPaths.contains(fi.path)) return@forEach
                    seenPaths.add(fi.path)
                    fileResults.add(fi)
                }
            } catch (_: Exception) {
                // ignore
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(query) {
        // debounce
        cancelSearch()
        val q = query
        if (q.isBlank()) {
            mediaResults.clear(); fileResults.clear(); loading = false; return@LaunchedEffect
        }
        delay(250)
        startSearch(q)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF010000))) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text(text = "Search", color = Color.White) }, navigationIcon = {
                IconButton(onClick = {
                    cancelSearch()
                    onNavigateBack()
                }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF010000)))

            TextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search by artist or track") }, modifier = Modifier.fillMaxWidth().padding(12.dp))

            if (!hasPermission) {
                Column(modifier = Modifier.fillMaxSize(),) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Permission required to search files", color = Color.White, modifier = Modifier.padding(12.dp))
                }
                return@Column
            }

            if (loading && mediaResults.isEmpty() && fileResults.isEmpty()) {
                // show a simple loading text
                Spacer(modifier = Modifier.height(12.dp))
                Text("Searching...", color = Color.White, modifier = Modifier.padding(12.dp))
            }

            FileList(
                files = fileResults,
                audioFiles = mediaResults,
                pathToId = pathToId,
                addToPlaylistName = addToPlaylistName,
                onFileSelected = { audioId ->
                    cancelSearch()
                    onFileSelected(audioId)
                },
                onEnterDirectory = { path ->
                    // Navigate into directory: open FileBrowserScreen at that path
                    try {
                        // quick hack: show toast and do nothing; Full navigation would need route with path param
                        Toast.makeText(context, "Open folder: ${File(path).name}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                },
                onShowOptions = { uri, title, artist, duration ->
                    selectedAudioUri = uri
                    selectedAudioTitle = title
                    selectedAudioArtist = artist
                    selectedAudioDuration = duration
                    playlists = PlaylistStore.loadAll(context).keys.toList()
                    showOptionsMenu = true
                }
            )
        }

        // Options sheet (single item actions)
        if (showOptionsMenu) {
            GenericOptionsSheet(
                visible = true,
                onDismiss = { showOptionsMenu = false },
                options = buildList {
                    add(
                        GenericOptionItem(
                            icon = Icons.Filled.Add,
                            label = "Add to playlist",
                            onClick = {
                                playlists = PlaylistStore.loadAll(context).keys.toList()
                                showOptionsMenu = false
                                showAddToPlaylistDialog = true
                            }
                        )
                    )
                }
            )
        }

        // Playlist selection sheet for adding a single audio item
        if (showAddToPlaylistDialog) {
            val onDismiss = { showAddToPlaylistDialog = false }
            GenericOptionsSheet(
                visible = showAddToPlaylistDialog,
                onDismiss = onDismiss,
                headerContent = { Text("Add to playlist", color = Color.White) },
                options = playlists.map { plName ->
                    GenericOptionItem(
                        label = plName,
                        leadingImageUri = PlaylistStore.getPlaylistImage(context, plName),
                        defaultLeadingPainterResourceId = com.example.bitsbeats.R.drawable.playlist_default,
                        onClick = {
                            // add the selected audio to this playlist
                            try {
                                val uri = selectedAudioUri
                                if (!uri.isNullOrBlank()) {
                                    PlaylistStore.addItemToPlaylist(context, plName, uri, selectedAudioTitle, selectedAudioArtist, selectedAudioDuration)
                                    Toast.makeText(context, "Added to '$plName'", Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not add to playlist", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            )
        }

        // Create playlist dialog for single add
        if (showCreatePlaylistForSingleDialog) {
            val onDismiss = { showCreatePlaylistForSingleDialog = false }
            val onConfirm = { playlistName: String ->
                try {
                    val uri = selectedAudioUri
                    val audioTitle = selectedAudioTitle
                    val audioArtist = selectedAudioArtist
                    val audioDuration = selectedAudioDuration
                    val added = if (uri != null) {
                        PlaylistStore.addItemToPlaylist(context, playlistName, uri, audioTitle, audioArtist, audioDuration)
                    } else false

                    if (added) {
                        Toast.makeText(context, "Playlist '$playlistName' created and item added", Toast.LENGTH_SHORT).show()
                        showCreatePlaylistForSingleDialog = false
                    } else {
                        Toast.makeText(context, "Error creating playlist", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show()
                }
            }

            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Create new playlist") },
                text = {
                    Column {
                        Text("Enter a name for the new playlist:")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(value = newPlaylistNameForSingle, onValueChange = { newPlaylistNameForSingle = it }, placeholder = { Text("Playlist name") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val name = newPlaylistNameForSingle.trim()
                        if (name.isNotEmpty()) {
                            onConfirm(name)
                        } else {
                            Toast.makeText(context, "Enter a playlist name", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Create") }
                },
                dismissButton = {
                    Button(onClick = onDismiss) { Text("Cancel") }
                }
            )
        }
    }
}
