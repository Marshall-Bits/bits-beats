package com.example.bitsbeats

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.os.Bundle
import android.provider.MediaStore
import java.net.URLEncoder
import java.net.URLDecoder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.bitsbeats.ui.components.PlaybackController
import com.example.bitsbeats.ui.theme.BitsBeatsTheme
import java.io.File
import com.example.bitsbeats.ui.screens.FileBrowserScreen
import com.example.bitsbeats.ui.screens.HomeScreen
import com.example.bitsbeats.ui.screens.PlayerScreen
import com.example.bitsbeats.ui.screens.PlaylistDetailScreen
import com.example.bitsbeats.ui.screens.PlaylistScreen


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
                            FileBrowserScreen(onFileSelected = { audioId ->
                                PlaybackController.playAudioId(
                                    appContext,
                                    audioId
                                )
                            }, onNavigateBack = { navController.popBackStack() })
                        }

                        composable("filebrowser/{addTo}") { backStackEntry ->
                            val encoded = backStackEntry.arguments?.getString("addTo") ?: ""
                            val playlistName = try { URLDecoder.decode(encoded, "UTF-8") } catch (_: Exception) { encoded }
                            FileBrowserScreen(
                                onFileSelected = { audioId ->
                                    PlaybackController.playAudioId(
                                        appContext,
                                        audioId
                                    )
                                },
                                onNavigateBack = { navController.popBackStack() },
                                addToPlaylistName = playlistName
                            )
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

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    BitsBeatsTheme {
        HomeScreen(onNavigateToPlayer = {}, onNavigateToPlaylist = {})
    }
}