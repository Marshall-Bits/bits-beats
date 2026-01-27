package com.example.bitsbeats

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
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

private const val NAV_TRANSITION_MS = 300

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure the native window background is black to avoid white flicker during cross-fades
        try { window.decorView.setBackgroundColor(android.graphics.Color.BLACK) } catch (_: Exception) {}

        // best-effort portrait fallback (not guaranteed on newer Android versions)
        try { requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT } catch (_: Exception) {}

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

                LaunchedEffect(Unit) {
                    // Ensure MediaPlaybackService is started early
                    try {
                        val svcIntent = android.content.Intent(appContext, MediaPlaybackService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            try { appContext.startForegroundService(svcIntent) } catch (_: Exception) { appContext.startService(svcIntent) }
                        } else {
                            try { appContext.startService(svcIntent) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}

                    try { PlaybackController.restoreState(appContext) } catch (_: Exception) {}
                }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    PortraitEnforcer {
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            enterTransition = { fadeIn(animationSpec = tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) },
                            exitTransition = { fadeOut(animationSpec = tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) },
                            popEnterTransition = { fadeIn(animationSpec = tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) },
                            popExitTransition = { fadeOut(animationSpec = tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) }
                        ) {
                            composable("home") {
                                HomeScreen(
                                    onNavigateToPlayer = { navController.navigate("player/-1") },
                                    onNavigateToPlaylist = { navController.navigate("playlist") },
                                    onNavigateToFileBrowser = { navController.navigate("filebrowser") },
                                    onNavigateToStats = { navController.navigate("stats") },
                                    onNavigateToPlaylistDetail = { name ->
                                        try {
                                            val enc = Uri.encode(name)
                                            navController.navigate("playlistDetail/$enc")
                                        } catch (_: Exception) {
                                            navController.navigate("playlist")
                                        }
                                    }
                                )
                            }

                            composable("player") {
                                PlayerScreen(audioId = -1L, restoreIfNoCurrent = false, onNavigateToPlaylistDetail = { name ->
                                    try {
                                        val enc = Uri.encode(name)
                                        navController.navigate("playlistDetail/$enc")
                                    } catch (_: Exception) {
                                        navController.navigate("playlist")
                                    }
                                })
                            }

                            composable("player/{audioId}") { backStackEntry ->
                                val audioId = backStackEntry.arguments?.getString("audioId")?.toLongOrNull() ?: -1L
                                PlayerScreen(audioId = audioId, onNavigateToPlaylistDetail = { name ->
                                    try {
                                        val enc = Uri.encode(name)
                                        navController.navigate("playlistDetail/$enc")
                                    } catch (_: Exception) {
                                        navController.navigate("playlist")
                                    }
                                })
                            }

                            composable("playlist") {
                                com.example.bitsbeats.ui.screens.PlaylistScreen(
                                    onNavigateToPlaylistDetail = { name: String ->
                                        val enc = Uri.encode(name)
                                        navController.navigate("playlistDetail/$enc")
                                    },
                                    onCreatePlaylist = {},
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("playlistDetail") {
                                com.example.bitsbeats.ui.screens.PlaylistScreen(
                                    onNavigateToPlaylistDetail = { name: String ->
                                        val enc = Uri.encode(name)
                                        navController.navigate("playlistDetail/$enc")
                                    },
                                    onCreatePlaylist = {},
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("playlistDetail/{id}") { backStackEntry ->
                                val encoded = backStackEntry.arguments?.getString("id") ?: ""
                                val id = try { Uri.decode(encoded) } catch (_: Exception) { encoded }
                                PlaylistDetailScreen(
                                    playlistName = id,
                                    onNavigateBack = { navController.popBackStack() },
                                    onAddSongs = {
                                        try { val enc = Uri.encode(id); navController.navigate("filebrowser/$enc") } catch (_: Exception) { navController.navigate("filebrowser") }
                                    }
                                )
                            }

                            composable("filebrowser") {
                                FileBrowserScreen(onFileSelected = { audioId -> PlaybackController.playAudioId(appContext, audioId) }, onNavigateBack = { navController.popBackStack() })
                            }

                            composable("filebrowser/{addTo}") { backStackEntry ->
                                val encoded = backStackEntry.arguments?.getString("addTo") ?: ""
                                val playlistName = try { Uri.decode(encoded) } catch (_: Exception) { encoded }
                                FileBrowserScreen(onFileSelected = { audioId -> PlaybackController.playAudioId(appContext, audioId) }, onNavigateBack = { navController.popBackStack() }, addToPlaylistName = playlistName)
                            }

                            composable("stats") { com.example.bitsbeats.ui.screens.StatsScreen(onNavigateBack = { navController.popBackStack() }) }

                            composable("search") { com.example.bitsbeats.ui.screens.SearchScreen(onFileSelected = { audioId: Long -> PlaybackController.playAudioId(appContext, audioId) }, onNavigateBack = { navController.popBackStack() }) }

                        }

                        // Mini player overlay
                        val navBackStack by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStack?.destination?.route ?: ""
                        val showMini = !currentRoute.startsWith("player") && PlaybackController.currentUri != null
                        val bottomNavHeight = 140.dp

                        AnimatedVisibility(
                            visible = showMini,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 8.dp)
                                .padding(bottom = bottomNavHeight)
                                .zIndex(0f),
                            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight * 3 }, animationSpec = tween(300)),
                            exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight * 3 }, animationSpec = tween(300))
                        ) {
                            PlaybackMiniPlayer(navController = navController, modifier = Modifier.fillMaxWidth().height(65.dp))
                        }

                        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomNavHeight).background(Color.Black.copy(alpha = 0.85f)).zIndex(1f), contentAlignment = Alignment.TopCenter) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { navController.navigate("home") }) {
                                    Icon(imageVector = Icons.Filled.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Home", color = Color.White, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { navController.navigate("playlist") }) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlists", tint = Color.White, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Playlists", color = Color.White, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { navController.navigate("search") }) {
                                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Search", color = Color.White, fontSize = 12.sp)
                                }
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
fun PortraitEnforcer(content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp
    val screenHeightDp = config.screenHeightDp
    val targetWidthDp = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) screenHeightDp else screenWidthDp
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.width(targetWidthDp.dp).fillMaxHeight()) { content() }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    BitsBeatsTheme { HomeScreen(onNavigateToPlayer = {}, onNavigateToPlaylist = {}) }
}
