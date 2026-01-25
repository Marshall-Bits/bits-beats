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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdCard
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.core.content.ContextCompat
import com.example.bitsbeats.data.AudioFile
import com.example.bitsbeats.data.FileItem
import com.example.bitsbeats.data.FileRepository.getDirectoryContents
import com.example.bitsbeats.data.MediaRepository.getRecentAudioFiles
import com.example.bitsbeats.data.MediaRepository.queryAudioIdFromPath
import com.example.bitsbeats.ui.components.PlaylistStore
import com.example.bitsbeats.ui.components.OptionsMenuSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.text.ifEmpty
import kotlin.text.startsWith

// Extend FileBrowserScreen to support adding to playlist and previewing
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
    LaunchedEffect(showOptionsMenu) {
        if (showOptionsMenu) {
            Toast.makeText(context, "showOptionsMenu = true", Toast.LENGTH_SHORT).show()
            Log.d("FileBrowserScreen", "showOptionsMenu = true")
        }
    }
    var selectedAudioUri by remember { mutableStateOf<String?>(null) }
    var selectedAudioTitle by remember { mutableStateOf("") }
    var selectedAudioArtist by remember { mutableStateOf("") }
    var selectedAudioDuration by remember { mutableStateOf(0L) }

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
                        text = if (!showFileBrowser) "Canciones Recientes" else File(currentPath).name.takeIf { it.isNotBlank() }
                            ?.let { if (it.length > 20) it.take(17) + "..." else it } // Limit folder name length with ellipsis
                            ?: "Almacenamiento",
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
                                if (parentPath != null && parentPath.startsWith(activeRoot)) {
                                    currentPath = parentPath
                                } else {
                                    // fallback: jump to active root
                                    currentPath = activeRoot
                                }
                            }
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
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
                            contentDescription = "Explorar",
                            tint = Color(0xFFFFD54F)
                        )
                    }
                    // "Añadir todo" icon: only visible when browsing and current directory has audio files
                    if (showFileBrowser && files.any { it.isAudio }) {
                        IconButton(onClick = {
                            // load playlists when opening dialog
                            playlists = PlaylistStore.loadAll(context).keys.toList()
                            showAddAllDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Añadir todo",
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
                    Text("Se necesita permiso para acceder a los archivos", color = Color.White)
                    Button(onClick = { permissionLauncher.launch(permission) }) { Text("Conceder permiso") }
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
                                    audio.artist.ifEmpty { "Artista desconocido" },
                                    color = Color.LightGray
                                )
                            }

                            // If we're in add-to-playlist flow we show '+' that adds directly to the provided playlist name
                            if (addToPlaylistName != null) {
                                IconButton(onClick = {
                                    val uri = ContentUris.withAppendedId(
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                        audio.id
                                    ).toString()
                                    PlaylistStore.addItemToPlaylist(
                                        context,
                                        addToPlaylistName,
                                        uri,
                                        audio.title,
                                        audio.artist,
                                        audio.duration
                                    )
                                    Toast.makeText(
                                        context,
                                        "Añadida a $addToPlaylistName",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Añadir",
                                        tint = Color.White
                                    )
                                }
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
                                        contentDescription = "Opciones",
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
                                if (resolvedId != null) onFileSelected(resolvedId) else Toast.makeText(context, "No indexado en MediaStore", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                                Text(fileItem.name, color = Color.White)
                            }

                            // If we're in add-to-playlist flow we show '+' that adds directly to the provided playlist name
                            if (fileItem.isAudio && addToPlaylistName != null) {
                                IconButton(onClick = {
                                    if (resolvedId != null) {
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
                                        } catch (_: Exception) {
                                        }
                                        PlaylistStore.addItemToPlaylist(
                                            context,
                                            addToPlaylistName,
                                            uri,
                                            title,
                                            artist,
                                            duration
                                        )
                                        Toast.makeText(
                                            context,
                                            "Añadida a $addToPlaylistName",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else Toast.makeText(
                                        context,
                                        "No indexado",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Añadir",
                                        tint = Color.White
                                    )
                                }
                            } else if (fileItem.isAudio && addToPlaylistName == null) {
                                // three-dot options for single file when not in add mode
                                IconButton(onClick = {
                                    // build selected metadata and open options
                                    val resolvedUri =
                                        if (resolvedId != null) ContentUris.withAppendedId(
                                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                            resolvedId
                                        ).toString() else Uri.fromFile(File(fileItem.path))
                                            .toString()
                                    selectedAudioUri = resolvedUri
                                    // try to fetch title/artist/duration when possible
                                    var title = File(fileItem.path).name
                                    var artist = ""
                                    var duration = 0L
                                    if (resolvedId != null) {
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
                                        } catch (_: Exception) {
                                        }
                                    }
                                    selectedAudioTitle = title
                                    selectedAudioArtist = artist
                                    selectedAudioDuration = duration
                                    playlists = PlaylistStore.loadAll(context).keys.toList()
                                    showOptionsMenu = true
                                    Log.d("FileBrowserScreen", "Options menu requested (file browser) for $title")
                                 }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Opciones",
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
            }

            // Add-all dialog: choose existing playlist or create new
            if (showAddAllDialog) {
                AlertDialog(
                    onDismissRequest = { showAddAllDialog = false },
                    title = { Text("Añadir todas las canciones") },
                    text = {
                        if (playlists.isEmpty()) {
                            Column {
                                Text("No tienes playlists. Puedes crear una nueva:")
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    showCreatePlaylistDialog = true
                                }) { Text("Crear nueva playlist") }
                            }
                        } else {
                            Column {
                                Text("Selecciona una playlist:")
                                Spacer(modifier = Modifier.height(8.dp))
                                // simple vertical list of buttons to pick playlist
                                playlists.forEach { p ->
                                    Button(
                                        onClick = {
                                            // add all audio files in current directory to playlist p
                                            val toAdd = files.filter { it.isAudio }
                                            toAdd.forEach { f ->
                                                try {
                                                    val resolvedId = pathToId[f.path]
                                                    val uri =
                                                        if (resolvedId != null) ContentUris.withAppendedId(
                                                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                                            resolvedId
                                                        ).toString() else Uri.fromFile(File(f.path))
                                                            .toString()
                                                    var title = File(f.path).name
                                                    var artist = ""
                                                    var duration = 0L
                                                    if (resolvedId != null) {
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
                                                        } catch (_: Exception) {
                                                        }
                                                    }
                                                    PlaylistStore.addItemToPlaylist(
                                                        context,
                                                        p,
                                                        uri,
                                                        title,
                                                        artist,
                                                        duration
                                                    )
                                                } catch (_: Exception) {
                                                }
                                            }
                                            Toast.makeText(
                                                context,
                                                "Añadidas ${toAdd.size} canciones a '$p'",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            showAddAllDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) { Text(p) }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showCreatePlaylistDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Crear nueva playlist") }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showAddAllDialog = false }) { Text("Cerrar") }
                    }
                )
            }

            // Create playlist dialog (when adding all and user wants to make a new playlist first)
            if (showCreatePlaylistDialog) {
                AlertDialog(
                    onDismissRequest = { showCreatePlaylistDialog = false },
                    title = { Text("Nombre de la playlist") },
                    text = {
                        Column {
                            TextField(
                                value = newPlaylistName,
                                onValueChange = { newPlaylistName = it },
                                placeholder = { Text("Nombre") })
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val name = newPlaylistName.trim()
                            if (name.isNotBlank()) {
                                val ok = PlaylistStore.createPlaylist(context, name)
                                if (ok) {
                                    // add all now
                                    val toAdd = files.filter { it.isAudio }
                                    toAdd.forEach { f ->
                                        try {
                                            val resolvedId = pathToId[f.path]
                                            val uri =
                                                if (resolvedId != null) ContentUris.withAppendedId(
                                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                                    resolvedId
                                                ).toString() else Uri.fromFile(File(f.path))
                                                    .toString()
                                            var title = File(f.path).name
                                            var artist = ""
                                            var duration = 0L
                                            if (resolvedId != null) {
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
                                                } catch (_: Exception) {
                                                }
                                            }
                                            PlaylistStore.addItemToPlaylist(
                                                context,
                                                name,
                                                uri,
                                                title,
                                                artist,
                                                duration
                                            )
                                        } catch (_: Exception) {
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        "Añadidas ${toAdd.size} canciones a '$name'",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showCreatePlaylistDialog = false
                                    showAddAllDialog = false
                                    newPlaylistName = ""
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Ya existe una playlist con ese nombre",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }) { Text("Crear y añadir") }
                    },
                    dismissButton = {
                        Button(onClick = { showCreatePlaylistDialog = false }) { Text("Cancelar") }
                    }
                )
            }

            // Dialog for adding single song to playlist
            if (showAddToPlaylistDialog) {
                AlertDialog(
                    onDismissRequest = { showAddToPlaylistDialog = false },
                    title = { Text("Añadir a la playlist") },
                    text = {
                        if (playlists.isEmpty()) {
                            Column {
                                Text("No tienes playlists. Puedes crear una nueva:")
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    showCreatePlaylistForSingleDialog = true
                                }) { Text("Crear nueva playlist") }
                            }
                        } else {
                            Column {
                                Text("Selecciona una playlist:")
                                Spacer(modifier = Modifier.height(8.dp))
                                playlists.forEach { p ->
                                    Button(
                                        onClick = {
                                            try {
                                                val uri = selectedAudioUri ?: ""
                                                PlaylistStore.addItemToPlaylist(
                                                    context,
                                                    p,
                                                    uri,
                                                    selectedAudioTitle,
                                                    selectedAudioArtist,
                                                    selectedAudioDuration
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "Añadida a '$p'",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } catch (_: Exception) {
                                            }
                                            showAddToPlaylistDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) { Text(p) }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showCreatePlaylistForSingleDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Crear nueva playlist") }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showAddToPlaylistDialog = false }) { Text("Cerrar") }
                    }
                )
            }

            // Create playlist dialog used when adding a single item (create and add)
            if (showCreatePlaylistForSingleDialog) {
                AlertDialog(
                    onDismissRequest = { showCreatePlaylistForSingleDialog = false },
                    title = { Text("Nombre de la playlist") },
                    text = {
                        Column {
                            TextField(
                                value = newPlaylistNameForSingle,
                                onValueChange = { newPlaylistNameForSingle = it },
                                placeholder = { Text("Nombre") })
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val name = newPlaylistNameForSingle.trim()
                            if (name.isNotBlank()) {
                                val ok = PlaylistStore.createPlaylist(context, name)
                                if (ok) {
                                    try {
                                        val uri = selectedAudioUri ?: ""
                                        PlaylistStore.addItemToPlaylist(
                                            context,
                                            name,
                                            uri,
                                            selectedAudioTitle,
                                            selectedAudioArtist,
                                            selectedAudioDuration
                                        )
                                        Toast.makeText(
                                            context,
                                            "Añadida a '$name'",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (_: Exception) {
                                    }
                                    showCreatePlaylistForSingleDialog = false
                                    showAddToPlaylistDialog = false
                                    newPlaylistNameForSingle = ""
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Ya existe una playlist con ese nombre",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }) { Text("Crear y añadir") }
                    },
                    dismissButton = {
                        Button(onClick = {
                            showCreatePlaylistForSingleDialog = false
                        }) { Text("Cancelar") }
                    }
                )
            }
        }

        // Use the new OptionsMenuSheet component from ui.components
        OptionsMenuSheet(
            visible = showOptionsMenu,
            onDismiss = { showOptionsMenu = false },
            selectedAudioUri = selectedAudioUri,
            selectedAudioTitle = selectedAudioTitle,
            selectedAudioArtist = selectedAudioArtist,
            onAddToPlaylistRequested = {
                playlists = PlaylistStore.loadAll(context).keys.toList()
                showAddToPlaylistDialog = true
                showOptionsMenu = false
            }
        )
    }}
