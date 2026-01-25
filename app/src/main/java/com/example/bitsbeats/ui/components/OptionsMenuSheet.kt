package com.example.bitsbeats.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import com.example.bitsbeats.R
import com.example.bitsbeats.ui.components.PlaylistStore
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image as FoundationImage

/**
 * Options menu sheet component shown from the bottom with overlay.
 * This component is self-contained UI and communicates via callbacks.
 * Implemented as a Dialog so it floats above navigation and mini-player.
 */
@Composable
fun OptionsMenuSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    selectedAudioUri: String?,
    selectedAudioTitle: String,
    selectedAudioArtist: String,
    onAddToPlaylistRequested: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // compute sizes in pixels
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val topMarginPx = with(density) { 48.dp.toPx() } // leave ~48dp at top
    val minHeightPx = screenHeightPx / 2f
    val maxHeightPx = (screenHeightPx - topMarginPx).coerceAtLeast(minHeightPx)

    // Animatable controlling current sheet height in px
    val sheetHeightPx = remember { Animatable(minHeightPx) }
    val sheetHeightDp = remember {
        derivedStateOf {
            with(density) { sheetHeightPx.value.toDp() }
        }
    }

    // artwork loaded asynchronously
    var artBitmap by remember(selectedAudioUri) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(
            null
        )
    }
    LaunchedEffect(selectedAudioUri) {
        artBitmap = try {
            selectedAudioUri?.let { com.example.bitsbeats.util.loadEmbeddedArtwork(context, it) }
        } catch (_: Exception) {
            null
        }
    }

    // Reset sheet height when shown
    LaunchedEffect(visible) {
        if (visible) {
            scope.launch { sheetHeightPx.snapTo(minHeightPx) }
        }
    }

    var showPlaylistPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // full-screen container so overlay covers everything
        Box(modifier = Modifier.fillMaxSize()) {
            // overlay with fade
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onDismiss() }
                )
            }

            // sheet content aligned bottom-center
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { full -> full },
                    animationSpec = tween(300)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { full -> full },
                    animationSpec = tween(250)
                )
            ) {
                // wrap to allow bottom alignment
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sheetHeightDp.value)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    // delta >0 when dragging down; reduce height when dragging down
                                    scope.launch {
                                        val new =
                                            (sheetHeightPx.value - delta).coerceIn(0f, maxHeightPx)
                                        sheetHeightPx.snapTo(new)
                                    }
                                },
                                onDragStopped = { velocity ->
                                    scope.launch {
                                        val current = sheetHeightPx.value
                                        // If released with fast downward velocity or height is small => dismiss
                                        if (velocity > 2000f || current < minHeightPx * 0.6f) {
                                            onDismiss()
                                        } else {
                                            // If dragged up beyond midpoint -> expand to max
                                            if (current > (minHeightPx + maxHeightPx) / 2f) {
                                                sheetHeightPx.animateTo(maxHeightPx, tween(250))
                                            } else {
                                                // snap back to min
                                                sheetHeightPx.animateTo(minHeightPx, tween(250))
                                            }
                                        }
                                    }
                                }
                            )
                            .background(
                                color = Color(0xFF0F0F0F),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        // small draggable handle (visual) - allows starting the drag from the top area
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .height(5.dp)
                                .width(40.dp)
                                .background(
                                    Color.LightGray.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, dragAmount ->
                                            scope.launch {
                                                val new =
                                                    (sheetHeightPx.value - dragAmount).coerceIn(
                                                        0f,
                                                        maxHeightPx
                                                    )
                                                sheetHeightPx.snapTo(new)
                                            }
                                        },
                                        onDragEnd = {
                                            scope.launch {
                                                val current = sheetHeightPx.value
                                                if (current < minHeightPx * 0.6f) {
                                                    onDismiss()
                                                } else if (current > (minHeightPx + maxHeightPx) / 2f) {
                                                    sheetHeightPx.animateTo(maxHeightPx, tween(250))
                                                } else {
                                                    sheetHeightPx.animateTo(minHeightPx, tween(250))
                                                }
                                            }
                                        }
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // artwork + metadata
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (artBitmap != null) {
                                Image(
                                    bitmap = artBitmap!!,
                                    contentDescription = "Artwork",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.song_default),
                                    contentDescription = "Default artwork",
                                    modifier = Modifier.size(64.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    selectedAudioTitle.ifEmpty { "Sin título" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    selectedAudioArtist.ifEmpty { "Artista desconocido" },
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Options list: each item is a row with icon on the left
                        // Add to playlist option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // open playlist picker
                                    showPlaylistPicker = true
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Añadir a playlist",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Añadir a playlist",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        // You can add more options here following the same pattern
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Playlist picker dialog (new) - appears above the options sheet
            if (showPlaylistPicker) {
                PlaylistPickerDialog(
                    visible = showPlaylistPicker,
                    onDismiss = { showPlaylistPicker = false },
                    selectedAudioUri = selectedAudioUri,
                    selectedAudioTitle = selectedAudioTitle,
                    selectedAudioArtist = selectedAudioArtist,
                    selectedAudioDuration = sheetHeightPx.value.toLong(), // placeholder if needed
                )
            }
        }
    }
}

@Composable
fun PlaylistPickerDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    selectedAudioUri: String?,
    selectedAudioTitle: String,
    selectedAudioArtist: String,
    selectedAudioDuration: Long,
) {
    if (!visible) return
    val context = LocalContext.current
    val playlistsMap = PlaylistStore.loadAll(context)
    val playlistNames = playlistsMap.keys.toList()

    // Make this picker stretchable similar to the main sheet
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val topMarginPx = with(density) { 48.dp.toPx() }
    val minHeightPx = screenHeightPx / 2f
    val maxHeightPx = (screenHeightPx - topMarginPx).coerceAtLeast(minHeightPx)
    val pickerHeightPx = remember { Animatable(minHeightPx) }
    val pickerHeightDp = remember {
        derivedStateOf { with(density) { pickerHeightPx.value.toDp() } }
    }

    LaunchedEffect(visible) {
        if (visible) scope.launch { pickerHeightPx.snapTo(minHeightPx) }
    }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onDismiss() }
            )

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pickerHeightDp.value)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    val new =
                                        (pickerHeightPx.value - delta).coerceIn(0f, maxHeightPx)
                                    pickerHeightPx.snapTo(new)
                                }
                            },
                            onDragStopped = { velocity ->
                                scope.launch {
                                    val current = pickerHeightPx.value
                                    // If released with fast downward velocity or height is small => dismiss
                                    if (velocity > 2000f || current < minHeightPx * 0.6f) {
                                        onDismiss()
                                    } else {
                                        // If dragged up beyond midpoint -> expand to max
                                        if (current > (minHeightPx + maxHeightPx) / 2f) {
                                            pickerHeightPx.animateTo(maxHeightPx, tween(250))
                                        } else {
                                            // snap back to min
                                            pickerHeightPx.animateTo(minHeightPx, tween(250))
                                        }
                                    }
                                }
                            }
                        )
                        .background(
                            color = Color(0xFF0F0F0F),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(12.dp)
                ) {
                    // top drag handle for the picker
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .height(5.dp)
                            .width(40.dp)

                            .background(
                                Color.LightGray.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        scope.launch {
                                            val new = (pickerHeightPx.value - dragAmount).coerceIn(
                                                0f,
                                                maxHeightPx
                                            )
                                            pickerHeightPx.snapTo(new)
                                        }
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            val current = pickerHeightPx.value
                                            if (current < minHeightPx * 0.6f) {
                                                onDismiss()
                                            } else if (current > (minHeightPx + maxHeightPx) / 2f) {
                                                pickerHeightPx.animateTo(maxHeightPx, tween(250))
                                            } else {
                                                pickerHeightPx.animateTo(minHeightPx, tween(250))
                                            }
                                        }
                                    }
                                )
                            }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Selecciona una playlist",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn {
                        items(playlistNames) { name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // playlist image (try load)
                                val img = PlaylistStore.getPlaylistImage(context, name)
                                var pBitmap by remember(img) {
                                    mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(
                                        null
                                    )
                                }
                                LaunchedEffect(img) {
                                    pBitmap = try {
                                        if (img.isNullOrBlank()) null else {
                                            if (img.startsWith("content://") || img.startsWith("file://") || img.startsWith(
                                                    "/"
                                                )
                                            ) {
                                                val uri = Uri.parse(img)
                                                val stream =
                                                    context.contentResolver.openInputStream(uri)
                                                val bmp = BitmapFactory.decodeStream(stream)
                                                stream?.close()
                                                bmp?.asImageBitmap()
                                            } else null
                                        }
                                    } catch (_: Exception) {
                                        null
                                    }
                                }

                                if (pBitmap != null) {
                                    FoundationImage(
                                        bitmap = pBitmap!!,
                                        contentDescription = "Playlist image",
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    FoundationImage(
                                        painter = painterResource(id = R.drawable.song_default),
                                        contentDescription = "Default playlist image",
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = name,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )

                                // plus-in-circle button
                                IconButton(onClick = {
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
                                    onDismiss()
                                }) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF2E2E2E), shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "Añadir"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
