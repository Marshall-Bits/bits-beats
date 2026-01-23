package com.example.bitsbeats

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.bitsbeats.ui.theme.BitsBeatsTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.ui.zIndex

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

// Central playback controller shared across screens (manages MediaPlayer & observable state)
object PlaybackController {
    var currentUri by mutableStateOf<String?>(null)
    var title by mutableStateOf("Sin canción")
    var artist by mutableStateOf("")
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableStateOf(0L)
    var duration by mutableStateOf(0L)

    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun playAudioId(context: Context, audioId: Long) {
        try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId)
            playUri(context, uri)
        } catch (_: Exception) {}
    }

    fun playUri(context: Context, uri: android.net.Uri) {
        try {
            // query metadata
            try {
                context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        title = c.getString(0) ?: title
                        artist = c.getString(1) ?: artist
                        duration = c.getLong(2)
                    }
                }
            } catch (_: Exception) {}

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer()
            mediaPlayer?.let { mp ->
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    mp.setDataSource(pfd.fileDescriptor)
                } ?: throw Exception("No se pudo abrir descriptor para $uri")
                mp.prepare()
                duration = mp.duration.toLong()
                currentUri = uri.toString()
                isPlaying = true
                mp.start()
                startTicker()
            }

            // persist last playback
            try {
                val prefs = context.getSharedPreferences("bitsbeats_prefs", Context.MODE_PRIVATE)
                val json = JSONObject().apply {
                    put("uri", currentUri ?: "")
                    put("position", 0L)
                    put("isPlaying", true)
                    put("updatedAt", System.currentTimeMillis())
                }
                prefs.edit { putString("last_playback", json.toString()) }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            isPlaying = false
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            isPlaying = false
            stopTicker()
        } else {
            mp.start()
            isPlaying = true
            startTicker()
        }
    }

    fun seekTo(ms: Int) {
        try { mediaPlayer?.seekTo(ms); currentPosition = ms.toLong() } catch (_: Exception) {}
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (true) {
                currentPosition = mediaPlayer?.currentPosition?.toLong() ?: 0L
                // persist occasionally
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun restoreLast(context: Context) {
        try {
            val prefs = context.getSharedPreferences("bitsbeats_prefs", Context.MODE_PRIVATE)
            val s = prefs.getString("last_playback", null) ?: return
            val json = JSONObject(s)
            val uriStr = json.optString("uri").takeIf { it.isNotBlank() } ?: return
            val pos = json.optLong("position", 0L)
            val wasPlaying = json.optBoolean("isPlaying", false)
            currentUri = uriStr
            try {
                val u = android.net.Uri.parse(uriStr)
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer()
                context.contentResolver.openFileDescriptor(u, "r")?.use { pfd -> mediaPlayer?.setDataSource(pfd.fileDescriptor) }
                mediaPlayer?.prepare()
                duration = mediaPlayer?.duration?.toLong() ?: 0L
                if (pos > 0) mediaPlayer?.seekTo(pos.toInt())
                currentPosition = pos
                if (wasPlaying) { mediaPlayer?.start(); isPlaying = true; startTicker() } else isPlaying = false
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun release() {
        stopTicker()
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        isPlaying = false
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitsBeatsTheme {
                val navController = rememberNavController()
                val appContext = LocalContext.current
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
                                onCreatePlaylist = { name ->
                                    // handled inside PlaylistScreen using PlaylistStore
                                }
                            )
                        }

                        composable("playlistDetail/{name}") { backStackEntry ->
                            val encoded = backStackEntry.arguments?.getString("name") ?: ""
                            val name = try { URLDecoder.decode(encoded, "UTF-8") } catch (e: Exception) { encoded }
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
                            val playlistName = try { URLDecoder.decode(encoded, "UTF-8") } catch (e: Exception) { encoded }
                            FileBrowserScreen(onFileSelected = { audioId -> PlaybackController.playAudioId(appContext, audioId) }, onNavigateBack = { navController.popBackStack() }, addToPlaylistName = playlistName)
                        }
                    }

                    // Mini player overlay: show on all screens except the PlayerScreen route, with slide up/down animations
                    val navBackStack by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStack?.destination?.route ?: ""
                    val showMini = !currentRoute.startsWith("player") && PlaybackController.currentUri != null
                    // Bottom navigation menu height
                    val bottomNavHeight = 64.dp

                    AnimatedVisibility(
                        visible = showMini,
                        // align AnimatedVisibility at bottom, respect system nav insets and pad upwards so mini-player sits neatly above the bottom nav menu
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 8.dp).padding(bottom = bottomNavHeight + 32.dp).zIndex(0f),
                        // start from further below so it appears to come from outside the screen edge
                        enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight * 2 }, animationSpec = tween(300)),
                        exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight * 2 }, animationSpec = tween(300))
                    ) {
                        // child should fill the width provided by the AnimatedVisibility container and have a controlled height
                        PlaybackMiniPlayer(navController = navController, modifier = Modifier.fillMaxWidth().height(64.dp))
                     }

                    // Bottom navigation menu: always visible at the bottom (above system nav), below mini-player
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp)
                            .fillMaxWidth()
                            .height(bottomNavHeight)
                            .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(12.dp))
                            .zIndex(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
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

@Composable
fun PlaybackMiniPlayer(navController: androidx.navigation.NavHostController, modifier: Modifier = Modifier) {
    val title = PlaybackController.title
    val artist = PlaybackController.artist
    val isPlaying = PlaybackController.isPlaying

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2D2D2D))
            // navigate to the 'player' route which will NOT reinitialize playback when a track is already loaded
            .clickable { navController.navigate("player") }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Filled.Album, contentDescription = "Artwork", tint = Color.White, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text(text = if (artist.isNotBlank()) artist else "Artista desconocido", color = Color.LightGray, maxLines = 1)
        }
        IconButton(onClick = { PlaybackController.togglePlayPause() }) {
            Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Reproducir", tint = Color.White)
        }
    }
}

@Composable
fun PlayerScreen(audioId: Long = -1L, restoreIfNoCurrent: Boolean = true) {
    val context = LocalContext.current

    // When navigated with an audioId play it; if -1 restore last
    LaunchedEffect(audioId) {
        if (audioId != -1L) {
            // explicit selection: play this audio id
            PlaybackController.playAudioId(context, audioId)
        } else {
            // navigation from mini-player or 'open last': if we already have a currentUri active
            // don't re-initialize playback when opening the player. Only attempt restore if nothing loaded.
            if (restoreIfNoCurrent && PlaybackController.currentUri == null) {
                PlaybackController.restoreLast(context)
            }
        }
    }

    // Observe controller state
    val title = PlaybackController.title
    val artist = PlaybackController.artist
    val isPlaying = PlaybackController.isPlaying
    val duration = PlaybackController.duration
    val currentPosition = PlaybackController.currentPosition

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(24.dp)) {
            Box(modifier = Modifier.size(200.dp).clip(CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Filled.Album, contentDescription = "Album", modifier = Modifier.size(180.dp), tint = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (artist.isNotBlank()) Text(text = artist, fontSize = 16.sp, color = Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                val sliderPos = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                Slider(value = sliderPos, onValueChange = { newValue -> PlaybackController.seekTo((newValue * duration).toInt()) }, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.Gray), modifier = Modifier.fillMaxWidth())

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatDuration(currentPosition), fontSize = 12.sp, color = Color.LightGray)
                    Text(text = formatDuration(duration), fontSize = 12.sp, color = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { PlaybackController.seekTo((PlaybackController.currentPosition - 10000L).coerceAtLeast(0L).toInt()) }, modifier = Modifier.size(64.dp)) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Retroceder", modifier = Modifier.size(48.dp), tint = Color.White) }

                IconButton(onClick = { PlaybackController.togglePlayPause() }, modifier = Modifier.size(80.dp)) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Reproducir", modifier = Modifier.size(64.dp), tint = Color.White) }

                IconButton(onClick = { PlaybackController.seekTo((PlaybackController.currentPosition + 10000L).coerceAtMost(PlaybackController.duration).toInt()) }, modifier = Modifier.size(64.dp)) { Icon(Icons.Filled.ChevronRight, contentDescription = "Avanzar", modifier = Modifier.size(48.dp), tint = Color.White) }
            }
        }
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
            androidx.compose.material3.AlertDialog(
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
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showingDialog = false },
                title = { Text("Nombre de la playlist") },
                text = {
                    androidx.compose.material3.TextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("Nombre") })
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
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { editingName = null },
                title = { Text("Editar nombre") },
                text = {
                    androidx.compose.material3.TextField(value = editText, onValueChange = { editText = it }, placeholder = { Text("Nuevo nombre") })
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

// Playlist detail screen: list songs, play entire playlist sequentially, add songs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(playlistName: String, onBack: () -> Unit = {}, onAddSongs: () -> Unit = {}) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(PlaylistStore.getPlaylist(context, playlistName)) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(0) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    fun playIndex(index: Int) {
        if (index < 0 || index >= items.size) return
        try {
            val uriString = items[index]["uri"] as? String ?: return
            val uri = uriString.toUri()
            mediaPlayer.reset()
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                mediaPlayer.setDataSource(pfd.fileDescriptor)
            } ?: throw Exception("No se pudo abrir descriptor")
            mediaPlayer.setOnCompletionListener {
                // advance
                if (currentIndex + 1 < items.size) {
                    currentIndex += 1
                    playIndex(currentIndex)
                } else {
                    isPlaying = false
                }
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
            isPlaying = true
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo reproducir: ${e.message}", Toast.LENGTH_SHORT).show()
            isPlaying = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().background(Color.DarkGray)) {
        TopAppBar(title = { Text(text = playlistName, color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2D2D2D)))

        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                if (!isPlaying) {
                    if (items.isNotEmpty()) { currentIndex = 0; playIndex(0) }
                } else {
                    mediaPlayer.pause(); isPlaying = false
                }
            }, enabled = items.isNotEmpty()) {
                Text(if (isPlaying) "PAUSE" else "PLAY")
            }
            Button(onClick = onAddSongs) { Text("Add songs") }
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay canciones en esta playlist", color = Color.White) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(items) { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item["title"] as? String ?: "Desconocido", color = Color.White)
                            Text(text = item["artist"] as? String ?: "Artista desconocido", color = Color.LightGray)
                        }
                        Text(text = formatDuration((item["duration"] as? Long) ?: 0L), color = Color.LightGray)
                    }
                }
            }
        }
    }
}

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
    val pathToId = remember { androidx.compose.runtime.mutableStateMapOf<String, Long?>() }

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
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        } else {
            // File browser: tap directories to enter, tap audio rows to play, '+' to add to playlist
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        }
    }
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
        } catch (e: Exception) { emptyMap() }
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
