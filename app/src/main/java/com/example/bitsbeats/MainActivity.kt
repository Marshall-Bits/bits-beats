package com.example.bitsbeats

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
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
import com.example.bitsbeats.ui.components.PlaybackMiniPlayer
import com.example.bitsbeats.ui.theme.BitsBeatsTheme
import com.example.bitsbeats.ui.screens.FileBrowserScreen
import com.example.bitsbeats.ui.screens.HomeScreen
import com.example.bitsbeats.ui.screens.PlayerScreen
import com.example.bitsbeats.ui.screens.PlaylistDetailScreen
import com.example.bitsbeats.ui.screens.PlaylistScreen

// PlaybackController moved to `PlaybackController.kt` (same package) to keep MainActivity concise
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request notification permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        enableEdgeToEdge()
        setContent {
            BitsBeatsTheme {
                val navController = rememberNavController()
                val appContext = LocalContext.current
                // restore persisted playback state once when the UI is composed
                LaunchedEffect(Unit) {
                    // Ensure MediaPlaybackService is started early so it can register its listener
                    try {
                        val svcIntent = android.content.Intent(appContext, com.example.bitsbeats.MediaPlaybackService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            try { appContext.startForegroundService(svcIntent) } catch (_: Exception) { appContext.startService(svcIntent) }
                        } else {
                            try { appContext.startService(svcIntent) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}

                    // restore persisted playback state once the service is available
                    try {
                        PlaybackController.restoreState(appContext)
                    } catch (_: Exception) {
                    }

                    // Ensure MediaPlaybackService is started if we have a restored playback or current track
                    try {
                        val svcIntent = android.content.Intent(appContext, com.example.bitsbeats.MediaPlaybackService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            try { appContext.startForegroundService(svcIntent) } catch (_: Exception) { appContext.startService(svcIntent) }
                        } else {
                            try { appContext.startService(svcIntent) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
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
                            val audioId =
                                backStackEntry.arguments?.getString("audioId")?.toLongOrNull()
                                    ?: -1L
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

                        // Route without parameter: opens playlists list (used when no active playlist is set)
                        composable("playlistDetail") {
                            PlaylistScreen(
                                onNavigateToPlaylistDetail = { name ->
                                    val enc = URLEncoder.encode(name, "UTF-8")
                                    navController.navigate("playlistDetail/$enc")
                                },
                                onCreatePlaylist = {}
                            )
                        }

                        composable("playlistDetail/{id}") { backStackEntry ->
                            val encoded = backStackEntry.arguments?.getString("id") ?: ""
                            val id = try {
                                URLDecoder.decode(encoded, "UTF-8")
                            } catch (_: Exception) {
                                encoded
                            }
                            PlaylistDetailScreen(
                                playlistName = id,
                                // navigate to the playlists list (route without parameter) when pressing back
                                onBackToList = { navController.navigate("playlistDetail") },
                                onAddSongs = {
                                    val enc = URLEncoder.encode(id, "UTF-8")
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
                            val playlistName = try {
                                URLDecoder.decode(encoded, "UTF-8")
                            } catch (_: Exception) {
                                encoded
                            }
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
                    val showMini =
                        !currentRoute.startsWith("player") && PlaybackController.currentUri != null
                    // Bottom navigation menu height
                    val bottomNavHeight = 140.dp

                    AnimatedVisibility(
                        visible = showMini,
                        // align AnimatedVisibility at bottom, respect system nav insets and pad upwards so mini-player sits neatly above the bottom nav menu
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 8.dp)
                            .padding(bottom = bottomNavHeight)
                            .zIndex(0f),
                        // start from further below so it appears to come from outside the screen edge
                        enter = slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight * 3 },
                            animationSpec = tween(300)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { fullHeight -> fullHeight * 3 },
                            animationSpec = tween(300)
                        )
                    ) {
                        // child should fill the width provided by the AnimatedVisibility container and have a controlled height
                        PlaybackMiniPlayer(
                            navController = navController,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(65.dp)
                        )
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Home
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { navController.navigate("home") }) {
                                Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = "Inicio",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Inicio", color = Color.White, fontSize = 12.sp)
                            }

                            // Playlists
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    val active = PlaybackController.activePlaylistName
                                    if (!active.isNullOrBlank()) {
                                        try {
                                            val enc = URLEncoder.encode(active, "UTF-8")
                                            navController.navigate("playlistDetail/$enc")
                                        } catch (_: Exception) {
                                            navController.navigate("playlist")
                                        }
                                    } else {
                                        navController.navigate("playlistDetail")
                                    }
                                }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = "Playlists",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Playlists", color = Color.White, fontSize = 12.sp)
                            }

                            // Search (unused for now)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { /* no-op */ }) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Buscar",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
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
        try {
            PlaybackController.release()
        } catch (_: Exception) {
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    BitsBeatsTheme {
        HomeScreen(onNavigateToPlayer = {}, onNavigateToPlaylist = {})
    }
}