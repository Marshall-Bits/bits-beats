package com.example.bitsbeats

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.net.URLEncoder
import java.net.URLDecoder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.bitsbeats.ui.theme.BitsBeatsTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import androidx.core.content.edit

// Data class para representar un archivo de audio
data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long
)

// Data class para representar un elemento del explorador de archivos
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isAudio: Boolean = false
)

// PlaybackController moved to `PlaybackController.kt` (same package) to keep MainActivity concise
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitsBeatsTheme {
                val navController = rememberNavController()
                val appContext = LocalContext.current
                // restore persisted playback state once when the UI is composed
                LaunchedEffect(Unit) {
                    try { PlaybackController.restoreState(appContext) } catch (_: Exception) {}
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        composable("home") {
                            HomeScreen(
                                onNavigateToPlayer = { navController.navigate("player/-1") },
                                onNavigateToPlaylist = { navController.navigate("playlist") },
                                onNavigateToFileBrowser = { navController.navigate("filebrowser") }
                            )
                        }

                        // Route to open player without changing playback (used by mini-player)
                        composable("player") {
                            PlayerScreen(audioId = -1L, restoreIfNoCurrent = false)
                        }

                        composable("player/{audioId}") { backStackEntry ->
                            val audioId = backStackEntry.arguments?.getString("audioId")?.toLongOrNull() ?: -1L
                            PlayerScreen(audioId = audioId)
                        }

                        composable("playlist") {
                            PlaylistScreen(
                                onNavigateToPlaylistDetail = { name ->
                                    val enc = URLEncoder.encode(name, "UTF-8")
                                    navController.navigate("playlistDetail/$enc")
                                },
                                onCreatePlaylist = {}
                            )
                        }

                        composable("playlistDetail/{name}") { backStackEntry ->
                            val encoded = backStackEntry.arguments?.getString("name") ?: ""
                            val name = try { URLDecoder.decode(encoded, "UTF-8") } catch (_: Exception) { encoded }
                            PlaylistDetailScreen(
                                playlistName = name,
                                onBack = { navController.popBackStack() },
                                onAddSongs = {
                                    val enc = URLEncoder.encode(name, "UTF-8")
                                    navController.navigate("filebrowser/$enc")
                                }
                            )
                        }

                        composable("filebrowser") {
                            FileBrowserScreen(onFileSelected = { audioId -> PlaybackController.playAudioId(appContext, audioId) }, onNavigateBack = { navController.popBackStack() })
                        }

                        composable("filebrowser/{addTo}") { backStackEntry ->
                            val encoded = backStackEntry.arguments?.getString("addTo") ?: ""
                            val playlistName = try { URLDecoder.decode(encoded, "UTF-8") } catch (_: Exception) { encoded }
                            FileBrowserScreen(onFileSelected = { audioId -> PlaybackController.playAudioId(appContext, audioId) }, onNavigateBack = { navController.popBackStack() }, addToPlaylistName = playlistName)
                        }
                    }

                    // Mini player overlay: show on all screens except the PlayerScreen route, with slide up/down animations
                    val navBackStack by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStack?.destination?.route ?: ""
                    val showMini = !currentRoute.startsWith("player") && PlaybackController.currentUri != null
                    // Bottom navigation menu height
                    val bottomNavHeight = 140.dp

                    AnimatedVisibility(
                        visible = showMini,
                        // align AnimatedVisibility at bottom, respect system nav insets and pad upwards so mini-player sits neatly above the bottom nav menu
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp).padding(bottom = bottomNavHeight).zIndex(0f),
                        // start from further below so it appears to come from outside the screen edge
                        enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight * 3 }, animationSpec = tween(300)),
                        exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight * 3 }, animationSpec = tween(300))
                    ) {
                        // child should fill the width provided by the AnimatedVisibility container and have a controlled height
                        PlaybackMiniPlayer(navController = navController, modifier = Modifier.fillMaxWidth().height(64.dp))
                     }

                    // Bottom navigation menu: always visible at the bottom (above system nav), below mini-player
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(bottomNavHeight)
                            .background(Color.Black.copy(alpha = 0.85f))
                            .zIndex(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            // Home
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { navController.navigate("home") }) {
                                Icon(imageVector = Icons.Filled.Home, contentDescription = "Inicio", tint = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Inicio", color = Color.White, fontSize = 12.sp)
                            }

                            // Playlists
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { navController.navigate("playlist") }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlists", tint = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Playlists", color = Color.White, fontSize = 12.sp)
                            }

                            // Search (unused for now)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { /* no-op */ }) {
                                Icon(imageVector = Icons.Filled.Search, contentDescription = "Buscar", tint = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Buscar", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { PlaybackController.release() } catch (_: Exception) {}
    }
}



// Función para obtener los últimos 10 archivos de audio del sistema (solo música real)
fun getRecentAudioFiles(contentResolver: ContentResolver): List<AudioFile> {
    val audioFiles = mutableListOf<AudioFile>()

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA
    )

    // Filtrar: solo música, duración > 30 segundos, excluir WhatsApp, notificaciones, etc.
    val selection = """
        ${MediaStore.Audio.Media.IS_MUSIC} != 0 
        AND ${MediaStore.Audio.Media.DURATION} > 30000
        AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%WhatsApp%'
        AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%Notifications%'
        AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%Ringtones%'
        AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%Alarms%'
    """.trimIndent()

    val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

    contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        var count = 0
        while (cursor.moveToNext() && count < 10) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn) ?: "Desconocido"
            val artistRaw = cursor.getString(artistColumn)
            val artist = if (artistRaw.isNullOrEmpty() || artistRaw == "<unknown>") "" else artistRaw
            val duration = cursor.getLong(durationColumn)

            audioFiles.add(AudioFile(id, title, artist, duration))
            count++
        }
    }

    return audioFiles
}

// Función para formatear la duración en mm:ss
@SuppressLint("DefaultLocale")
fun formatDuration(durationMs: Long): String {
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    return String.format("%d:%02d", minutes, seconds)
}

// Función para obtener el contenido de un directorio (usada por el explorador)
fun getDirectoryContents(path: String): List<FileItem> {
    val file = File(path)
    val items = mutableListOf<FileItem>()

    if (!file.exists() || !file.isDirectory) {
        return items
    }

    val audioExtensions = listOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "wma")

    file.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.forEach { child ->
        val extension = child.extension.lowercase()
        val isAudio = audioExtensions.contains(extension)

        if (child.isDirectory || isAudio) {
            items.add(
                FileItem(
                    name = child.name,
                    path = child.absolutePath,
                    isDirectory = child.isDirectory,
                    isAudio = isAudio
                )
            )
        }
    }

    return items
}

// Helper para resolver una ruta de archivo a un audioId de MediaStore
fun queryAudioIdFromPath(contentResolver: ContentResolver, filePath: String): Long? {
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
    val selection = "${MediaStore.Audio.Media.DATA} = ?"
    val selectionArgs = arrayOf(filePath)

    contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            return cursor.getLong(idColumn)
        }
    }
    return null
}

// Playlist storage helpers (simple JSON in SharedPreferences)
object PlaylistStore {
    private const val PREFS = "bitsbeats_prefs"
    private const val KEY_PLAYLISTS = "playlists_json"

    // Format: JSON object { "playlistName": [ {"uri": "...", "title":"...","artist":"...","duration":12345}, ... ], ... }
    fun loadAll(context: Context): Map<String, List<Map<String, Any>>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyMap()
        return try {
            val root = JSONObject(s)
            val result = mutableMapOf<String, List<Map<String, Any>>>()
            root.keys().forEach { name ->
                val arr = root.optJSONArray(name) ?: return@forEach
                val list = mutableListOf<Map<String, Any>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val map = mutableMapOf<String, Any>()
                    map["uri"] = obj.optString("uri")
                    map["title"] = obj.optString("title")
                    map["artist"] = obj.optString("artist")
                    map["duration"] = obj.optLong("duration", 0L)
                    list.add(map)
                }
                result[name] = list
            }
            result
        } catch (_: Exception) { emptyMap() }
    }

    private fun saveAll(context: Context, data: Map<String, List<Map<String, Any>>>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = JSONObject()
        data.forEach { (k, v) ->
            val arr = org.json.JSONArray()
            v.forEach { item ->
                val obj = JSONObject()
                obj.put("uri", item["uri"] ?: "")
                obj.put("title", item["title"] ?: "")
                obj.put("artist", item["artist"] ?: "")
                obj.put("duration", item["duration"] ?: 0L)
                arr.put(obj)
            }
            root.put(k, arr)
        }
        prefs.edit { putString(KEY_PLAYLISTS, root.toString()) }
    }

    fun createPlaylist(context: Context, name: String): Boolean {
        val all = loadAll(context).toMutableMap()
        if (all.containsKey(name)) return false
        all[name] = emptyList()
        saveAll(context, all)
        return true
    }

    fun deletePlaylist(context: Context, name: String) {
        val all = loadAll(context).toMutableMap()
        all.remove(name)
        saveAll(context, all)
    }

    fun renamePlaylist(context: Context, oldName: String, newName: String): Boolean {
        val all = loadAll(context).toMutableMap()
        if (!all.containsKey(oldName)) return false
        if (all.containsKey(newName)) return false
        val value = all.remove(oldName) ?: emptyList()
        all[newName] = value
        saveAll(context, all)
        return true
    }

    fun addItemToPlaylist(context: Context, playlistName: String, uri: String, title: String, artist: String, duration: Long) {
        val all = loadAll(context).toMutableMap()
        val list = all[playlistName]?.toMutableList() ?: mutableListOf()
        val item = mapOf("uri" to uri, "title" to title, "artist" to artist, "duration" to duration)
        list.add(item)
        all[playlistName] = list
        saveAll(context, all)
    }

    fun getPlaylist(context: Context, name: String): List<Map<String, Any>> {
        return loadAll(context)[name] ?: emptyList()
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    BitsBeatsTheme {
        HomeScreen(onNavigateToPlayer = {}, onNavigateToPlaylist = {})
    }
}

@Composable
fun HomeScreen(onNavigateToPlayer: () -> Unit, onNavigateToPlaylist: () -> Unit, onNavigateToFileBrowser: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Bits Beats",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón de reproducción
                IconButton(
                    onClick = onNavigateToPlayer,
                    modifier = Modifier.size(100.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Album,
                        contentDescription = "Ir a reproducción",
                        modifier = Modifier.size(80.dp),
                        tint = Color.White
                    )
                }

                // Botón de playlist
                IconButton(
                    onClick = onNavigateToPlaylist,
                    modifier = Modifier.size(100.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Ir a playlist",
                        modifier = Modifier.size(80.dp),
                        tint = Color.White
                    )
                }

                // Botón de explorador de archivos
                IconButton(
                    onClick = onNavigateToFileBrowser,
                    modifier = Modifier.size(100.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Explorar archivos",
                        modifier = Modifier.size(80.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
