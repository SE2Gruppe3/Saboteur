package com.aau.saboteur.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
        ) {
            if (bitmap != null) {
                Image(
                    painter = BitmapPainter(bitmap!!),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp, bottom = 64.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .pointerInput(Unit) {
                            coroutineScope {
                                launch {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                                        scale = newScale
                                        if (newScale > 1f) {
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

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color(0xFF2A2A2A), shape = RoundedCornerShape(24.dp))
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
                    .padding(8.dp)
                    .background(Color(0xFF2A2A2A), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.schliessen),
                    tint = Color.White
                )
            }

            Text(
                text = stringResource(R.string.spielregeln_titel),
                color = Color(0xFFFFD700),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 14.dp)
            )
        }
    }
}
