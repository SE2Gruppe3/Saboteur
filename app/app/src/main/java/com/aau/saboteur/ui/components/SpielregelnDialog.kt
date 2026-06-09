package com.aau.saboteur.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aau.saboteur.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val DialogBackground = Color(0xFF1A1A1A)
private val NavBarBackground = Color(0xFF2A2A2A)

@Composable
fun SpielregelnDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val fileName = if (language == "de") "Spielanleitung_Saboteur.pdf" else "Rulebook_Saboteur.pdf"

    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var parcelFd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var tempFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(fileName) {
        val tf = withContext(Dispatchers.IO) {
            val f = File.createTempFile("spielregeln", ".pdf", context.cacheDir)
            context.assets.open(fileName).use { input -> f.outputStream().use { input.copyTo(it) } }
            f
        }
        val parcel = ParcelFileDescriptor.open(tf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(parcel)
        parcelFd = parcel
        tempFile = tf
        pdfRenderer = renderer
        pageCount = renderer.pageCount
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer?.close()
            parcelFd?.close()
            tempFile?.delete()
        }
    }

    LaunchedEffect(currentPage, pdfRenderer) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            val page = renderer.openPage(currentPage)
            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bmp
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DialogBackground)
        ) {
            Text(
                text = stringResource(R.string.spielregeln_titel),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Text("✕", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }

            bitmap?.let { bmp ->
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { it.setImageBitmap(bmp) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp, bottom = 72.dp)
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(NavBarBackground)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0
                ) {
                    Text("‹", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                }

                if (pageCount > 0) {
                    Text(
                        text = stringResource(R.string.seite_x_von_y, currentPage + 1, pageCount),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                TextButton(
                    onClick = { if (currentPage < pageCount - 1) currentPage++ },
                    enabled = currentPage < pageCount - 1
                ) {
                    Text("›", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                }
            }
        }
    }
}
