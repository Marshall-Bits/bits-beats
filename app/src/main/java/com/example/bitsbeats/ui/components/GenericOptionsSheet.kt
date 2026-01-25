package com.example.bitsbeats.ui.components

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable

/**
 * GenericOptionsSheet: reusable bottom sheet with overlay, handle, stretchable behaviour,
 * and a list of option items. Supports optional leading image URI and trailing content per item.
 */
data class GenericOptionItem(
    val label: String,
    val icon: ImageVector? = null,
    val leadingImageUri: String? = null,
    val iconTint: Color = Color.White,
    val rowClickable: Boolean = true,
    val onClick: () -> Unit,
    val trailingContent: (@Composable (() -> Unit))? = null
)

@Composable
fun GenericOptionsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    headerContent: (@Composable () -> Unit)? = null,
    options: List<GenericOptionItem>
) {
    if (!visible) return

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val topMarginPx = with(density) { 48.dp.toPx() }
    val minHeightPx = screenHeightPx / 2f
    val maxHeightPx = (screenHeightPx - topMarginPx).coerceAtLeast(minHeightPx)

    val sheetHeightPx = remember { Animatable(minHeightPx) }
    val sheetHeightDp = remember { derivedStateOf { with(density) { sheetHeightPx.value.toDp() } } }

    LaunchedEffect(visible) {
        if (visible) scope.launch { sheetHeightPx.snapTo(minHeightPx) }
    }

    Dialog(onDismissRequest = { onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(200)), exit = fadeOut(animationSpec = tween(200))) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() })
            }

            AnimatedVisibility(visible = true, enter = slideInVertically(initialOffsetY = { full -> full }, animationSpec = tween(300)), exit = slideOutVertically(targetOffsetY = { full -> full }, animationSpec = tween(250))) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sheetHeightDp.value)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    scope.launch { val new = (sheetHeightPx.value - delta).coerceIn(0f, maxHeightPx); sheetHeightPx.snapTo(new) }
                                },
                                onDragStopped = { velocity ->
                                    scope.launch {
                                        val current = sheetHeightPx.value
                                        if (velocity > 2000f || current < minHeightPx * 0.6f) onDismiss() else if (current > (minHeightPx + maxHeightPx) / 2f) sheetHeightPx.animateTo(maxHeightPx, tween(250)) else sheetHeightPx.animateTo(minHeightPx, tween(250))
                                    }
                                }
                            )
                            .background(color = Color(0xFF0F0F0F), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .padding(16.dp)
                    ) {
                        // handle
                        Box(modifier = Modifier.align(Alignment.CenterHorizontally).height(6.dp).width(40.dp).clip(RoundedCornerShape(3.dp)).background(Color.LightGray.copy(alpha = 0.15f)))
                        Spacer(modifier = Modifier.height(10.dp))

                        headerContent?.let { it(); Spacer(modifier = Modifier.height(12.dp)) }

                        options.forEach { opt ->
                            // leading image loader if provided
                            var leadBitmap by remember(opt.leadingImageUri) { mutableStateOf<ImageBitmap?>(null) }
                            LaunchedEffect(opt.leadingImageUri) {
                                leadBitmap = try {
                                    opt.leadingImageUri?.let { uriStr ->
                                        val uri = android.net.Uri.parse(uriStr)
                                        ctx.contentResolver.openInputStream(uri)?.use { stream ->
                                            val bmp = BitmapFactory.decodeStream(stream)
                                            bmp?.asImageBitmap()
                                        }
                                    }
                                } catch (_: Exception) { null }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (opt.rowClickable) Modifier.clickable { try { opt.onClick() } catch (_: Exception) {}; onDismiss() } else Modifier)
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (leadBitmap != null) {
                                    Image(bitmap = leadBitmap!!, contentDescription = opt.label, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
                                } else if (opt.icon != null) {
                                    Icon(imageVector = opt.icon, contentDescription = opt.label, tint = opt.iconTint)
                                }

                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = opt.label, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))

                                // trailing content (e.g., plus button)
                                opt.trailingContent?.let { tc -> tc() }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
