package com.aau.saboteur.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aau.saboteur.R
import com.aau.saboteur.util.LanguageManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SpielregelnDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang by LanguageManager.currentLanguage

    val pageFileNames = if (lang.startsWith("de")) listOf(
        "spielanleitung_saboteur_s1.png",
        "spielanleitung_saboteur_s2.png"
    ) else listOf(
        "rulebook_saboteur_s1.png",
        "rulebook_saboteur_s2.png"
    )

    var currentPage by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(currentPage, lang) {
        bitmap = null
        withContext(Dispatchers.IO) {
            val bmp = context.assets.open(pageFileNames[currentPage])
                .use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()
            withContext(Dispatchers.Main) { bitmap = bmp }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()
    val isZoomed = scale > 1.01f

    LaunchedEffect(currentPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        scrollState.scrollTo(0)
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
            Box(modifier = Modifier.fillMaxSize()) {

                // Scrollable container – disabled while zoomed so pan takes over
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (!isZoomed) Modifier.verticalScroll(scrollState) else Modifier)
                ) {
                    if (bitmap != null) {
                        val bmp = bitmap!!
                        Image(
                            painter = BitmapPainter(bmp),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY,
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                )
                                .pointerInput(Unit) {
                                    coroutineScope {
                                        launch {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                                scale = newScale
                                                if (newScale > 1.01f) {
                                                    offsetX += pan.x
                                                    offsetY += pan.y
                                                } else {
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                }
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
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color(0xFFFFD700)
                        )
                    }
                }

                // X button – top-end overlay
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 8.dp)
                        .zIndex(2f)
                        .background(Color(0x99000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.schliessen),
                        tint = Color.LightGray
                    )
                }

                // Navigation – bottom-center overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .zIndex(2f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0x99000000), CircleShape)
                            .clickable(enabled = currentPage > 0) { currentPage-- },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Vorherige Seite",
                            tint = if (currentPage > 0) Color.White else Color.Gray
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0x99000000), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${currentPage + 1} / ${pageFileNames.size}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0x99000000), CircleShape)
                            .clickable(enabled = currentPage < pageFileNames.size - 1) {
                                currentPage++
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Nächste Seite",
                            tint = if (currentPage < pageFileNames.size - 1) Color.White else Color.Gray
                        )
                    }
                }
            }
        }
    }
}
