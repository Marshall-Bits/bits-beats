package com.example.bitsbeats

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bitsbeats.ui.theme.BitsBeatsTheme
import kotlinx.coroutines.delay

// Data class para representar un archivo de audio
data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitsBeatsTheme {
                val navController = rememberNavController()
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
                            onNavigateToPlaylist = { navController.navigate("playlist") }
                        )
                    }
                    composable("player/{audioId}") { backStackEntry ->
                        val audioId = backStackEntry.arguments?.getString("audioId")?.toLongOrNull() ?: -1L
                        PlayerScreen(audioId = audioId)
                    }
                    composable("playlist") {
                        PlaylistScreen(
                            onAudioSelected = { audioId ->
                                navController.navigate("player/$audioId")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onNavigateToPlayer: () -> Unit, onNavigateToPlaylist: () -> Unit) {
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
                text = "Hola!",
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
                        imageVector = Icons.Filled.MusicNote,
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
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Ir a playlist",
                        modifier = Modifier.size(80.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(audioId: Long = -1L) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var audioTitle by remember { mutableStateOf("Sin canción seleccionada") }
    var audioArtist by remember { mutableStateOf("") }

    val mediaPlayer = remember { MediaPlayer() }

    // Cargar y reproducir el audio cuando se selecciona
    LaunchedEffect(audioId) {
        if (audioId != -1L) {
            try {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    audioId
                )

                // Obtener info del audio
                val projection = arrayOf(
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST
                )
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        audioTitle = cursor.getString(0) ?: "Desconocido"
                        audioArtist = cursor.getString(1) ?: "Artista desconocido"
                    }
                }

                mediaPlayer.reset()
                mediaPlayer.setDataSource(context, uri)
                mediaPlayer.prepare()
                duration = mediaPlayer.duration.toLong()
                mediaPlayer.start()
                isPlaying = true
            } catch (e: Exception) {
                audioTitle = "Error al reproducir"
                audioArtist = e.message ?: ""
            }
        }
    }

    // Actualizar la posición de reproducción
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = mediaPlayer.currentPosition.toLong()
            if (duration > 0) {
                sliderPosition = currentPosition.toFloat() / duration.toFloat()
            }
            delay(100)
        }
    }

    // Liberar el MediaPlayer cuando se sale de la pantalla
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Imagen del disco
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Album,
                    contentDescription = "Album",
                    modifier = Modifier.size(180.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Título y artista
            Text(
                text = audioTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (audioArtist.isNotEmpty()) {
                Text(
                    text = audioArtist,
                    fontSize = 16.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Barra de progreso
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = sliderPosition,
                    onValueChange = { newValue ->
                        sliderPosition = newValue
                        val newPosition = (newValue * duration).toLong()
                        mediaPlayer.seekTo(newPosition.toInt())
                        currentPosition = newPosition
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Tiempos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Text(
                        text = formatDuration(duration),
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botones de reproducción
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        // Retroceder 10 segundos
                        val newPosition = (currentPosition - 10000).coerceAtLeast(0)
                        mediaPlayer.seekTo(newPosition.toInt())
                        currentPosition = newPosition
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "Retroceder",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = {
                        // Avanzar 10 segundos
                        val newPosition = (currentPosition + 10000).coerceAtMost(duration)
                        mediaPlayer.seekTo(newPosition.toInt())
                        currentPosition = newPosition
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Avanzar",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
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
            val artist = cursor.getString(artistColumn) ?: "Artista desconocido"
            val duration = cursor.getLong(durationColumn)

            audioFiles.add(AudioFile(id, title, artist, duration))
            count++
        }
    }

    return audioFiles
}

// Función para formatear la duración en mm:ss
fun formatDuration(durationMs: Long): String {
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun PlaylistScreen(onAudioSelected: (Long) -> Unit = {}) {
    val context = LocalContext.current
    var audioFiles by remember { mutableStateOf<List<AudioFile>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            audioFiles = getRecentAudioFiles(context.contentResolver)
        }
    }

    // Verificar permiso al iniciar
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            audioFiles = getRecentAudioFiles(context.contentResolver)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Archivos de Audio",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (!hasPermission) {
                // Mostrar botón para solicitar permiso
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Se necesita permiso para acceder a los archivos de audio",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = { permissionLauncher.launch(permission) }) {
                        Text("Conceder permiso")
                    }
                }
            } else if (audioFiles.isEmpty()) {
                // No hay archivos de audio
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron archivos de audio",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            } else {
                // Mostrar lista de archivos de audio
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(audioFiles) { audio ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Gray, shape = RoundedCornerShape(12.dp))
                                .clickable { onAudioSelected(audio.id) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = "Audio",
                                modifier = Modifier.size(40.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.size(16.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = audio.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = audio.artist,
                                    fontSize = 14.sp,
                                    color = Color.LightGray
                                )
                            }
                            Text(
                                text = formatDuration(audio.duration),
                                fontSize = 14.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
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