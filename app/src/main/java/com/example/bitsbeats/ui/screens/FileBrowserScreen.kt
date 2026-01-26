package com.example.bitsbeats.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.core.content.ContextCompat
import com.example.bitsbeats.data.AudioFile
import com.example.bitsbeats.data.FileItem
import com.example.bitsbeats.data.FileRepository.getDirectoryContents
import com.example.bitsbeats.data.MediaRepository.getRecentAudioFiles
import com.example.bitsbeats.data.MediaRepository.queryAudioIdFromPath
import com.example.bitsbeats.ui.components.PlaylistStore
import com.example.bitsbeats.ui.components.GenericOptionsSheet
import com.example.bitsbeats.ui.components.GenericOptionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.text.ifEmpty
import kotlin.text.startsWith
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

// Reusable animated '+' button with shorter success pop and a failure shake.
@Composable
private fun AnimatedAddButton(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    onAdd: () -> Boolean
) {
    val scope = rememberCoroutineScope()
    val animScaleX = remember { Animatable(1f) }
    val animScaleY = remember { Animatable(1f) }
    val animOffsetX = remember { Animatable(0f) } // for shake on failure

    IconButton(onClick = {
        val added = try {
            onAdd()
        } catch (_: Exception) {
            false
        }
        // Run animation depending on success/failure
        scope.launch {
            if (added) {
                val peakMs = 100
                val backMs = 100
                // peak: scale to 2x and rotate to +40deg in parallel
                val j1 = launch { animScaleX.animateTo(2f, animationSpec = tween(durationMillis = peakMs, easing = FastOutSlowInEasing)) }
                val j2 = launch { animScaleY.animateTo(2f, animationSpec = tween(durationMillis = peakMs, easing = FastOutSlowInEasing)) }
                j1.join()
                j2.join()

                // return to identity quickly with same non-linear easing
                val k1 = launch { animScaleX.animateTo(1f, animationSpec = tween(durationMillis = backMs, easing = FastOutSlowInEasing)) }
                val k2 = launch { animScaleY.animateTo(1f, animationSpec = tween(durationMillis = backMs, easing = FastOutSlowInEasing)) }
                k1.join()
                k2.join()

                // ensure offset reset
                animOffsetX.animateTo(0f, animationSpec = tween(30))
            } else {
                // FAILURE / already added: small left-right shake, total ~100ms
                // sequence timings: left 30ms, right 30ms, small left 20ms, return 20ms = 100ms
                val leftMs = 30
                val rightMs = 30
                val smallLeftMs = 20
                val backMs = 20
                val dist = 10f
                animOffsetX.animateTo(-dist, animationSpec = tween(durationMillis = leftMs, easing = FastOutSlowInEasing))
                animOffsetX.animateTo(dist, animationSpec = tween(durationMillis = rightMs, easing = FastOutSlowInEasing))
                animOffsetX.animateTo(-dist * 0.5f, animationSpec = tween(durationMillis = smallLeftMs, easing = FastOutSlowInEasing))
                animOffsetX.animateTo(0f, animationSpec = tween(durationMillis = backMs, easing = FastOutSlowInEasing))

            }
        }
    }) {
        Box(modifier = modifier.graphicsLayer {
            scaleX = animScaleX.value
            scaleY = animScaleY.value
            translationX = animOffsetX.value
        }, contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Add", tint = tint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onFileSelected: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    addToPlaylistName: String? = null
) {
    val context = LocalContext.current
    var showFileBrowser by remember { mutableStateOf(false) }
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var audioFiles by remember { mutableStateOf<List<AudioFile>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }
    // detected removable SD card root path (null when not found)
    var sdCardRoot by remember { mutableStateOf<String?>(null) }

    // UI + state for "add all" feature
    var showAddAllDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var playlists by remember { mutableStateOf<List<String>>(emptyList()) }

    // cache mapping from file path -> MediaStore audio id (nullable)
    val pathToId = remember { mutableStateMapOf<String, Long?>() }

    // --- New state for options menu (sheet) and single-item add flow ---
    var showOptionsMenu by remember { mutableStateOf(false) }
    var selectedAudioUri by remember { mutableStateOf<String?>(null) }
    var selectedAudioTitle by remember { mutableStateOf("") }
    var selectedAudioArtist by remember { mutableStateOf("") }
    var selectedAudioDuration by remember { mutableLongStateOf(0L) }

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistForSingleDialog by remember { mutableStateOf(false) }
    var newPlaylistNameForSingle by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { /* nothing local to release */ } }

    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                audioFiles = getRecentAudioFiles(context.contentResolver)
                files = getDirectoryContents(currentPath)
            }
        }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            audioFiles = getRecentAudioFiles(context.contentResolver)
            files = getDirectoryContents(currentPath)
            // try to detect a removable SD card root by checking external files dirs
            try {
                val dirs = context.getExternalFilesDirs(null)
                val primary = Environment.getExternalStorageDirectory().absolutePath
                dirs?.forEach { d ->
                    if (d == null) return@forEach
                    val root = d.absolutePath.substringBefore("/Android")
                    if (root.isNotBlank() && root != primary) {
                        sdCardRoot = root
                        return@forEach
                    }
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }

    LaunchedEffect(currentPath) {
        if (hasPermission) {
            files = getDirectoryContents(currentPath)
            // populate cache for audio files
            withContext(Dispatchers.IO) {
                val newMap = mutableMapOf<String, Long?>()
                files.filter { it.isAudio }.forEach { f ->
                    try {
                        val id = queryAudioIdFromPath(context.contentResolver, f.path)
                        newMap[f.path] = id
                    } catch (_: Exception) {
                        newMap[f.path] = null
                    }
                }
                newMap.forEach { (k, v) -> pathToId[k] = v }
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF010000))) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = if (!showFileBrowser) "Recent tracks" else File(currentPath).name.takeIf { it.isNotBlank() }
                            ?.let { if (it.length > 20) it.take(17) + "..." else it } // Limit folder name length with ellipsis
                            ?: "Storage",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showFileBrowser) {
                            // Determine the active root (SD card when current path is on SD, otherwise primary)
                            val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
                            val activeRoot =
                                sdCardRoot?.takeIf { currentPath.startsWith(it) } ?: primaryRoot

                            // If we're at the active root, go back to recent songs; otherwise go one directory up
                            if (currentPath == activeRoot) {
                                showFileBrowser = false
                            } else {
                                val parentPath = File(currentPath).parent
                                currentPath = if (parentPath != null && parentPath.startsWith(activeRoot)) {
                                    parentPath
                                } else {
                                    // fallback: jump to active root
                                    activeRoot
                                }
                            }
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // SD card quick navigation (only shown when detected)
                    if (sdCardRoot != null) {
                        IconButton(onClick = {
                            showFileBrowser = true
                            currentPath = sdCardRoot ?: currentPath
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SdCard,
                                contentDescription = "SD Card",
                                tint = Color(0xFFFFD54F)
                            )
                        }
                    }
                    // Folder icon: always open the primary system storage root and show file browser
                    IconButton(onClick = {
                        showFileBrowser = true
                        currentPath = Environment.getExternalStorageDirectory().absolutePath
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Explore",
                            tint = Color(0xFFFFD54F)
                        )
                    }
                    // "Add all" icon: only visible when browsing and current directory has audio files
                    if (showFileBrowser && files.any { it.isAudio }) {
                        IconButton(onClick = {
                            // load playlists when opening dialog
                            playlists = PlaylistStore.loadAll(context).keys.toList()
                            showAddAllDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add all",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF010000))
                // make top app bar match global background #010000
            )

            if (!hasPermission) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Permission required to access data", color = Color.White)
                    Button(onClick = { permissionLauncher.launch(permission) }) { Text("Authorize") }
                }
                return@Column
            }

            if (!showFileBrowser) {
                // Recent audio list: tap the row to play, '+' to add to playlist when applicable
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    items(audioFiles) { audio ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // thumbnail
                            val ctx = LocalContext.current
                            val audioUriString = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                audio.id
                            ).toString()
                            var embeddedBitmap by remember(audioUriString) {
                                mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(
                                    null
                                )
                            }
                            LaunchedEffect(audioUriString) {
                                embeddedBitmap = try {
                                    com.example.bitsbeats.util.loadEmbeddedArtwork(
                                        ctx,
                                        audioUriString
                                    )
                                } catch (_: Exception) {
                                    null
                                }
                            }

                            if (embeddedBitmap != null) {
                                Image(
                                    bitmap = embeddedBitmap!!,
                                    contentDescription = "Artwork",
                                    modifier = Modifier.size(48.dp).clip(CircleShape).clickable { onFileSelected(audio.id) }
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = com.example.bitsbeats.R.drawable.song_default),
                                    contentDescription = "Default artwork",
                                    modifier = Modifier.size(48.dp).clip(CircleShape).clickable { onFileSelected(audio.id) }
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f).clickable { onFileSelected(audio.id) }) {
                                Text(audio.title, color = Color.White)
                                Text(
                                    audio.artist.ifEmpty { "Unknown artist" },
                                    color = Color.LightGray
                                )
                            }

                            // If we're in add-to-playlist flow we show '+' that adds directly to the provided playlist name
                            if (addToPlaylistName != null) {
                                // replaced plain IconButton+Toast with AnimatedAddButton; success toast removed
                                AnimatedAddButton(tint = Color.White, onAdd = {
                                    try {
                                        val uri = ContentUris.withAppendedId(
                                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                            audio.id
                                        ).toString()
                                        // return whether the item was actually added
                                        PlaylistStore.addItemToPlaylist(
                                            context,
                                            addToPlaylistName,
                                            uri,
                                            audio.title,
                                            audio.artist,
                                            audio.duration
                                        )
                                    } catch (_: Exception) {
                                        false
                                    }
                                })
                            } else {
                                // not in "add mode": show three-dot options icon that opens the bottom sheet
                                IconButton(onClick = {
                                    // populate selected audio metadata and open options menu
                                    selectedAudioUri = ContentUris.withAppendedId(
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                        audio.id
                                    ).toString()
                                    selectedAudioTitle = audio.title
                                    selectedAudioArtist = audio.artist
                                    selectedAudioDuration = audio.duration
                                    playlists = PlaylistStore.loadAll(context).keys.toList()
                                    showOptionsMenu = true
                                    Log.d("FileBrowserScreen", "Options menu requested (recent list) for ${audio.title}")
                                 }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Options",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(200.dp))
                    }
                }
            } else {
                // File browser: tap directories to enter, tap audio rows to play, '+' to add to playlist
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    items(files) { fileItem ->
                        val resolvedId = if (fileItem.isAudio) pathToId[fileItem.path] else null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                            // Left icon: folder for directories, thumbnail for audio, default if none
                            if (fileItem.isDirectory) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = "Folder",
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else if (fileItem.isAudio) {
                                val ctx = LocalContext.current
                                val uriString = if (resolvedId != null) ContentUris.withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    resolvedId
                                ).toString() else Uri.fromFile(File(fileItem.path)).toString()
                                var embeddedBitmap by remember(uriString) {
                                    mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(
                                        null
                                    )
                                }
                                LaunchedEffect(uriString) {
                                    embeddedBitmap = try {
                                        com.example.bitsbeats.util.loadEmbeddedArtwork(
                                            ctx,
                                            uriString
                                        )
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (embeddedBitmap != null) {
                                    Image(
                                        bitmap = embeddedBitmap!!,
                                        contentDescription = "Artwork",
                                        modifier = Modifier.size(48.dp).clip(CircleShape).clickable { if (resolvedId != null) onFileSelected(resolvedId) }
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = com.example.bitsbeats.R.drawable.song_default),
                                        contentDescription = "Default artwork",
                                        modifier = Modifier.size(48.dp).clip(CircleShape).clickable { if (resolvedId != null) onFileSelected(resolvedId) }
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                // fallback for other file types
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = "File",
                                    tint = Color.LightGray
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Column(modifier = Modifier.weight(1f).clickable {
                            if (fileItem.isDirectory) currentPath = fileItem.path else if (fileItem.isAudio) {
                                if (resolvedId != null) onFileSelected(resolvedId) else Toast.makeText(context, "Not indexed in MediaStore", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                                Text(fileItem.name, color = Color.White)
                            }

                            // If we're in add-to-playlist flow we show '+' that adds directly to the provided playlist name
                            if (fileItem.isAudio && addToPlaylistName != null) {
                                AnimatedAddButton(tint = Color.White, onAdd = {
                                    if (resolvedId == null) return@AnimatedAddButton false
                                    try {
                                        val uri = ContentUris.withAppendedId(
                                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                            resolvedId
                                        ).toString()
                                        var title = File(fileItem.path).name
                                        var artist = ""
                                        var duration = 0L
                                        try {
                                            context.contentResolver.query(
                                                ContentUris.withAppendedId(
                                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                                    resolvedId
                                                ),
                                                arrayOf(
                                                    MediaStore.Audio.Media.TITLE,
                                                    MediaStore.Audio.Media.ARTIST,
                                                    MediaStore.Audio.Media.DURATION
                                                ),
                                                null,
                                                null,
                                                null
                                            )?.use { c ->
                                                if (c.moveToFirst()) {
                                                    title = c.getString(0) ?: title
                                                    artist = c.getString(1) ?: ""
                                                    duration = c.getLong(2)
                                                }
                                            }
                                        } catch (_: Exception) { /* ignore metadata errors */ }

                                        // return whether it was actually added (false if duplicate)
                                        return@AnimatedAddButton PlaylistStore.addItemToPlaylist(
                                            context,
                                            addToPlaylistName,
                                            uri,
                                            title,
                                            artist,
                                            duration
                                        )
                                    } catch (_: Exception) {
                                        return@AnimatedAddButton false
                                    }
                                })
                             }
                         }
                     }
                 }
             }
         }
     }

    // --- "Add all" dialog: confirm adding all files in the current directory to a playlist ---
    if (showAddAllDialog) {
        val onDismiss = { showAddAllDialog = false }
        val onConfirm = { playlistName: String ->
            // add all audio files in the current directory to the selected playlist
            try {
                files.filter { it.isAudio }.forEach { fileItem ->
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        queryAudioIdFromPath(context.contentResolver, fileItem.path) ?: -1
                    ).toString()
                    PlaylistStore.addItemToPlaylist(context, playlistName, uri)
                }
                Toast.makeText(context, "Added all to '$playlistName'", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Error adding files", Toast.LENGTH_SHORT).show()
            }
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add all to playlist") },
            text = {
                Column {
                    Text("Select a playlist to add all files in this folder:")
                    Spacer(modifier = Modifier.height(8.dp))
                    // list available playlists as buttons
                    playlists.forEach { playlistName ->
                        Button(
                            onClick = {
                                onConfirm(playlistName)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(playlistName)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    // "New playlist" option
                    Button(
                        onClick = {
                            showCreatePlaylistDialog = true
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("New playlist")
                    }
                 }
             },
            confirmButton = {},
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Create new playlist dialog (for "add all" or single items) ---
    if (showCreatePlaylistDialog || showCreatePlaylistForSingleDialog) {
        val isSingleItem = showCreatePlaylistForSingleDialog
        val onDismiss = { if (isSingleItem) showCreatePlaylistForSingleDialog = false else showCreatePlaylistDialog = false }
        val onConfirm = { playlistName: String ->
            // create the new playlist and add the selected item(s)
            try {
                val uri = selectedAudioUri?.let {
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.toLong())
                }
                val audioTitle = selectedAudioTitle
                val audioArtist = selectedAudioArtist
                val audioDuration = selectedAudioDuration
                val added = if (isSingleItem && uri != null) {
                    // single item: add to new playlist immediately
                    PlaylistStore.addItemToPlaylist(context, playlistName, uri.toString(), audioTitle, audioArtist, audioDuration)
                } else {
                    // "Add all" case: just create the empty playlist
                    PlaylistStore.createPlaylist(context, playlistName)
                }
                if (added) {
                    Toast.makeText(context, "Playlist '$playlistName' created", Toast.LENGTH_SHORT).show()
                    showCreatePlaylistDialog = false
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
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Playlist name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                 }
             },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            onConfirm(name)
                        } else {
                            Toast.makeText(context, "Enter a playlist name", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Options menu (bottom sheet) for audio file actions ---
    if (showOptionsMenu) {
        GenericOptionsSheet(
            visible = true,
            onDismiss = { showOptionsMenu = false },
            options = buildList {
                // "Play now" action: play the selected audio file immediately
                add(
                    GenericOptionItem(
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        label = "Play now",
                        onClick = {
                            showOptionsMenu = false
                            onFileSelected(queryAudioIdFromPath(context.contentResolver, selectedAudioUri!!) ?: -1)
                        }
                    )
                )
                // "Add to playlist" action: show dialog to select a playlist
                add(
                    GenericOptionItem(
                        icon = Icons.Filled.Add,
                        label = "Add to playlist",
                        onClick = {
                            showOptionsMenu = false
                            // load playlists and show dialog
                            playlists = PlaylistStore.loadAll(context).keys.toList()
                            showAddToPlaylistDialog = true
                        }
                    )
                )
                // "Share" action: share the selected audio file
                add(
                    GenericOptionItem(
                        icon = Icons.Filled.Share,
                        label = "Share",
                        onClick = {
                            showOptionsMenu = false
                            // TODO: implement share functionality
                            Toast.makeText(context, "Share not implemented", Toast.LENGTH_SHORT).show()
                        }
                    )
                )
                // "Delete" action: delete the selected audio file (with confirmation)
                add(
                    GenericOptionItem(
                        icon = Icons.Filled.Delete,
                        label = "Delete",
                        onClick = {
                            showOptionsMenu = false
                            // TODO: implement delete functionality
                            Toast.makeText(context, "Delete not implemented", Toast.LENGTH_SHORT).show()
                        }
                    )
                )
            }
        )
    }
}
