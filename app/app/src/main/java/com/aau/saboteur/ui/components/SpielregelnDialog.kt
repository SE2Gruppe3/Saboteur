package com.aau.saboteur.ui.components

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.aau.saboteur.R
import com.aau.saboteur.util.LanguageManager
import com.aau.saboteur.util.rememberLocalizedContext

@Composable
fun SpielregelnDialog(onDismiss: () -> Unit) {
    val language by LanguageManager.currentLanguage
    val localizedContext = rememberLocalizedContext(language)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // FIX: Der CompositionLocalProvider muss INSIDE des Dialogs sein,
        // da Dialogs in einem eigenen Fenster laufen und Locals sonst nicht vererbt werden.
        CompositionLocalProvider(LocalContext provides localizedContext) {
            val imagePages = if (language.startsWith("de")) listOf(
                "spielanleitung_saboteur_s1.png",
                "spielanleitung_saboteur_s2.png"
            ) else listOf(
                "rulebook_saboteur_s1.png",
                "rulebook_saboteur_s2.png"
            )

            val totalPages = imagePages.size + 1
            var currentPage by remember { mutableIntStateOf(0) }

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                color = Color(0xFF1A1A1A)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    
                    // 1. Der Inhalt (Vollbild)
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (currentPage < imagePages.size) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.builtInZoomControls = true
                                        settings.displayZoomControls = false
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        settings.setSupportZoom(true)
                                        setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
                                        
                                        val html = """
                                            <html>
                                            <body style="margin:0; padding:0; background-color:#1A1A1A;">
                                                <img src="file:///android_asset/${imagePages[currentPage]}" style="width:100%;" />
                                                <div style="height: 180px;"></div> <!-- Scroll-Puffer unten -->
                                            </body>
                                            </html>
                                        """.trimIndent()
                                        loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                                    }
                                },
                                update = { webView ->
                                    if (currentPage < imagePages.size) {
                                        val html = """
                                            <html>
                                            <body style="margin:0; padding:0; background-color:#1A1A1A;">
                                                <img src="file:///android_asset/${imagePages[currentPage]}" style="width:100%;" />
                                                <div style="height: 180px;"></div>
                                            </body>
                                            </html>
                                        """.trimIndent()
                                        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                                    }
                                }
                            )
                        } else {
                            CheatRulesPage(modifier = Modifier.fillMaxSize())
                        }
                    }

                    // 2. Schließen-Button (fest oben rechts)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 16.dp, end = 16.dp)
                            .zIndex(10f)
                            .background(Color(0x99000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.schliessen),
                            tint = Color.White
                        )
                    }

                    // 3. Navigations-Balken (SCHWEBEND)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding() 
                            .padding(bottom = 75.dp)
                            .zIndex(10f)
                            .background(Color(0x99000000), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0,
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (currentPage > 0) Color(0xFF555555).copy(alpha = 0.7f) else Color(0x22555555),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = null,
                                tint = if (currentPage > 0) Color.White else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "${currentPage + 1} / $totalPages",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = { if (currentPage < totalPages - 1) currentPage++ },
                            enabled = currentPage < totalPages - 1,
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (currentPage < totalPages - 1) Color(0xFF555555).copy(alpha = 0.7f) else Color(0x22555555),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = if (currentPage < totalPages - 1) Color.White else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheatRulesPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 80.dp, bottom = 180.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.cheat_rules_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFFE0C097),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        CheatSection(
            title = stringResource(R.string.cheat_lantern_title),
            description = stringResource(R.string.cheat_lantern_desc)
        )
        Spacer(modifier = Modifier.height(20.dp))

        // 2. NEU: Volume Button Cheat
        CheatSection(
            title = stringResource(R.string.cheat_volume_title),
            description = stringResource(R.string.cheat_volume_desc)
        )

        Spacer(modifier = Modifier.height(20.dp))

        CheatSection(
            title = stringResource(R.string.cheat_risk_title),
            description = stringResource(R.string.cheat_risk_desc)
        )
    }
}

@Composable
private fun CheatSection(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            lineHeight = 20.sp
        )
    }
}
