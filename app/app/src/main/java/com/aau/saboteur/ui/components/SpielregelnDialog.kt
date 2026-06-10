package com.aau.saboteur.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aau.saboteur.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpielregelnDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalConfiguration.current.locales[0].language

    val pageFileNames = if (lang == "de") listOf(
        "spielanleitung_saboteur_s1.png",
        "spielanleitung_saboteur_s2.png"
    ) else listOf(
        "rulebook_saboteur_s1.png",
        "rulebook_saboteur_s2.png"
    )

    var currentPage by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Pixel-Größen für Bounds-Berechnung
    var imageHeightPx by remember { mutableIntStateOf(0) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentPage) {
        bitmap = null
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        withContext(Dispatchers.IO) {
            val bmp = context.assets.open(pageFileNames[currentPage])
                .use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()
            withContext(Dispatchers.Main) { bitmap = bmp }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            color = Color(0xFF1A1A1A)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged {
                        containerWidthPx = it.width
                        containerHeightPx = it.height
                    }
            ) {
                if (bitmap != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = BitmapPainter(bitmap!!),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .onSizeChanged { imageHeightPx = it.height }
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY,
                                    // Zoom-Pivot = obere Mitte → kein Ruckler, Bild wächst nach unten
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                )
                                .pointerInput(Unit) {
                                    coroutineScope {
                                        launch {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                                scale = newScale

                                                // Y: Bild immer lückenlos von oben bis unten
                                                val minY = (containerHeightPx - imageHeightPx * newScale)
                                                    .coerceAtMost(0f)
                                                offsetY = (offsetY + pan.y).coerceIn(minY, 0f)

                                                // X: bei scale=1 kein seitliches Verschieben,
                                                //    bei zoom Bild deckt immer volle Breite ab
                                                val maxX = containerWidthPx * (newScale - 1f) / 2f
                                                offsetX = if (newScale > 1f)
                                                    (offsetX + pan.x).coerceIn(-maxX, maxX)
                                                else 0f
                                            }
                                        }
                                        launch {
                                            detectTapGestures(onDoubleTap = {
                                                scale = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                            })
                                        }
                                    }
                                }
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFFFFD700)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                        .background(Color(0xCC2A2A2A), shape = RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Vorherige Seite",
                            tint = if (currentPage > 0) Color(0xFFFFD700) else Color.Gray
                        )
                    }
                    Text(
                        text = "${currentPage + 1} / ${pageFileNames.size}",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    IconButton(
                        onClick = { if (currentPage < pageFileNames.size - 1) currentPage++ },
                        enabled = currentPage < pageFileNames.size - 1
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Nächste Seite",
                            tint = if (currentPage < pageFileNames.size - 1) Color(0xFFFFD700) else Color.Gray
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 8.dp, top = 4.dp)
                        .background(Color(0xCC2A2A2A), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.schliessen),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
