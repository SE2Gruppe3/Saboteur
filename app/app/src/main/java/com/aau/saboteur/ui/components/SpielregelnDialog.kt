package com.aau.saboteur.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aau.saboteur.R
import com.aau.saboteur.util.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

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
    var layoutWidth by remember { mutableFloatStateOf(0f) }
    val zoomState = rememberZoomState()

    LaunchedEffect(currentPage, lang) {
        zoomState.reset()
        bitmap = null
        withContext(Dispatchers.IO) {
            val bmp = context.assets.open(pageFileNames[currentPage])
                .use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()
            withContext(Dispatchers.Main) { bitmap = bmp }
        }
    }

    // Set content size in layout coordinates so zoomable knows the full rendered height.
    // contentScale=FillWidth renders at width=layoutWidth, height=layoutWidth*(bmp.h/bmp.w).
    LaunchedEffect(bitmap, layoutWidth) {
        val bmp = bitmap ?: return@LaunchedEffect
        if (layoutWidth <= 0f) return@LaunchedEffect
        val renderedHeight = layoutWidth * bmp.height.toFloat() / bmp.width.toFloat()
        zoomState.setContentSize(Size(layoutWidth, renderedHeight))
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

                // Image container – TopStart so zoomable starts at the top
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (bitmap != null) {
                        val bmp = bitmap!!
                        Image(
                            painter = BitmapPainter(bmp),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { layoutWidth = it.width.toFloat() }
                                .zoomable(zoomState)
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
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0,
                        modifier = Modifier.background(Color(0x99000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Vorherige Seite",
                            tint = if (currentPage > 0) Color.LightGray else Color.LightGray.copy(alpha = 0.3f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0x99000000), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${currentPage + 1} / ${pageFileNames.size}",
                            color = Color.LightGray,
                            fontSize = 16.sp
                        )
                    }

                    IconButton(
                        onClick = { if (currentPage < pageFileNames.size - 1) currentPage++ },
                        enabled = currentPage < pageFileNames.size - 1,
                        modifier = Modifier.background(Color(0x99000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Nächste Seite",
                            tint = if (currentPage < pageFileNames.size - 1) Color.LightGray else Color.LightGray.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}
