package com.example.bitsbeats.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bitsbeats.AudioFile
import com.example.bitsbeats.FileItem
import com.example.bitsbeats.ui.components.PlaylistStore
import com.example.bitsbeats.getDirectoryContents
import com.example.bitsbeats.getRecentAudioFiles
import com.example.bitsbeats.queryAudioIdFromPath
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

    // cache mapping from file path -> MediaStore audio id (nullable)
    val pathToId = remember { mutableStateMapOf<String, Long?>() }

    DisposableEffect(Unit) { onDispose { /* nothing local to release */ } }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            audioFiles = getRecentAudioFiles(context.contentResolver)
            files = getDirectoryContents(currentPath)
        }
    }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            audioFiles = getRecentAudioFiles(context.contentResolver)
            files = getDirectoryContents(currentPath)
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

    Column(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
        TopAppBar(
            title = { Text(text = if (!showFileBrowser) "Canciones Recientes" else File(currentPath).name.takeIf { it.isNotBlank() } ?: "Almacenamiento", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = {
                    if (showFileBrowser) {
                        val parentPath = File(currentPath).parent
                        if (parentPath != null && parentPath.startsWith(Environment.getExternalStorageDirectory().absolutePath)) {
                            currentPath = parentPath
                        } else {
                            showFileBrowser = false
                        }
                    } else {
                        onNavigateBack()
                    }
                }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                }
            },
            actions = { IconButton(onClick = { showFileBrowser = !showFileBrowser }) { Icon(imageVector = Icons.Filled.Folder, contentDescription = "Explorar", tint = Color(0xFFFFD54F)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2D2D2D))
        )

        if (!hasPermission) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Se necesita permiso para acceder a los archivos", color = Color.White)
                Button(onClick = { permissionLauncher.launch(permission) }) { Text("Conceder permiso") }
            }
            return@Column
        }

        if (!showFileBrowser) {
            // Recent audio list: tap the row to play, '+' to add to playlist when applicable
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item{
                    Spacer(modifier = Modifier.height(12.dp) )
                }
                items(audioFiles) { audio ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Gray, shape = RoundedCornerShape(12.dp))
                            .clickable { onFileSelected(audio.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(audio.title, color = Color.White)
                            Text(audio.artist.ifEmpty { "Artista desconocido" }, color = Color.LightGray)
                        }

                        if (addToPlaylistName != null) {
                            IconButton(onClick = {
                                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audio.id).toString()
                                PlaylistStore.addItemToPlaylist(context, addToPlaylistName, uri, audio.title, audio.artist, audio.duration)
                                Toast.makeText(context, "Añadida a $addToPlaylistName", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(imageVector = Icons.Filled.Add, contentDescription = "Añadir", tint = Color.White)
                            }
                        }
                    }
                }
                item{
                    Spacer(modifier = Modifier.height(200.dp) )
                }
            }
        } else {
            // File browser: tap directories to enter, tap audio rows to play, '+' to add to playlist
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item{
                    Spacer(modifier = Modifier.height(12.dp) )
                }
                items(files) { fileItem ->
                    val resolvedId = if (fileItem.isAudio) pathToId[fileItem.path] else null

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (fileItem.isDirectory) {
                                    currentPath = fileItem.path
                                } else if (fileItem.isAudio) {
                                    val rid = resolvedId
                                    if (rid != null) onFileSelected(rid) else Toast.makeText(context, "No indexado en MediaStore", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .background(if (fileItem.isDirectory) Color(0xFF4A4A4A) else Color.Gray, shape = RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fileItem.name, color = Color.White)
                        }

                        if (fileItem.isAudio && addToPlaylistName != null) {
                            IconButton(onClick = {
                                if (resolvedId != null) {
                                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, resolvedId).toString()
                                    var title = File(fileItem.path).name
                                    var artist = ""
                                    var duration = 0L
                                    try {
                                        context.contentResolver.query(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, resolvedId), arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION), null, null, null)?.use { c ->
                                            if (c.moveToFirst()) {
                                                title = c.getString(0) ?: title
                                                artist = c.getString(1) ?: ""
                                                duration = c.getLong(2)
                                            }
                                        }
                                    } catch (_: Exception) {}
                                    PlaylistStore.addItemToPlaylist(context, addToPlaylistName, uri, title, artist, duration)
                                    Toast.makeText(context, "Añadida a $addToPlaylistName", Toast.LENGTH_SHORT).show()
                                } else Toast.makeText(context, "No indexado", Toast.LENGTH_SHORT).show()
                            }) { Icon(imageVector = Icons.Filled.Add, contentDescription = "Añadir", tint = Color.White) }
                        }
                    }
                }
                item{
                    Spacer(modifier = Modifier.height(200.dp) )
                }
            }
        }
    }
}