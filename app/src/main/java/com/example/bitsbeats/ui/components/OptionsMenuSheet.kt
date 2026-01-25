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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import com.example.bitsbeats.R

/**
 * Options menu sheet component shown from the bottom with overlay.
 * This component is self-contained UI and communicates via callbacks.
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
    val context = LocalContext.current

    // sheet height: half-screen approximation; caller can adjust by composing inside their layout
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val sheetHeightDp = (config.screenHeightDp / 2).dp

    // artwork loaded asynchronously
    var artBitmap by remember(selectedAudioUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(selectedAudioUri) {
        artBitmap = try {
            selectedAudioUri?.let { com.example.bitsbeats.util.loadEmbeddedArtwork(context, it) }
        } catch (_: Exception) { null }
    }

    // overlay
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { full -> full }, animationSpec = tween(300)),
        exit = slideOutVertically(targetOffsetY = { full -> full }, animationSpec = tween(250))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeightDp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 20) onDismiss()
                        }
                    }
                    .background(color = Color(0xFF0F0F0F), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (artBitmap != null) {
                        Image(bitmap = artBitmap!!, contentDescription = "Artwork", modifier = Modifier.size(64.dp).clip(CircleShape))
                    } else {
                        Image(painter = painterResource(id = R.drawable.song_default), contentDescription = "Default artwork", modifier = Modifier.size(64.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(selectedAudioTitle.ifEmpty { "Sin título" }, color = Color.White)
                        Text(selectedAudioArtist.ifEmpty { "Artista desconocido" }, color = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(12.dp))

                Button(onClick = {
                    onAddToPlaylistRequested()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add to playlist")
                }
            }
        }
    }
}
