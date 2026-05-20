package com.aau.saboteur.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppLanguage { DE, EN }

@Composable
fun LobbyMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onLeaveGame: (() -> Unit)? = null,
    showLeaveGame: Boolean = true
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        LanguageSelector()

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        VolumeSliderRow(volume = volume, onVolumeChange = onVolumeChange)

        if (showLeaveGame && onLeaveGame != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("Leave Game", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onLeaveGame()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@Composable
fun LanguageSelector() {
    var selectedLanguage by remember { mutableStateOf(AppLanguage.DE) }
    var showAlternative by remember { mutableStateOf(false) }

    val currentFlag     = if (selectedLanguage == AppLanguage.DE) "🇩🇪" else "🇬🇧"
    val alternativeFlag = if (selectedLanguage == AppLanguage.DE) "🇬🇧" else "🇩🇪"
    val alternativeLang = if (selectedLanguage == AppLanguage.DE) AppLanguage.EN else AppLanguage.DE

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        FlagChip(flag = currentFlag, onClick = { showAlternative = !showAlternative })

        AnimatedVisibility(
            visible = showAlternative,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                FlagChip(
                    flag = alternativeFlag,
                    onClick = {
                        selectedLanguage = alternativeLang
                        showAlternative = false
                        // TODO: LocaleHelper.setLocale(context, alternativeLang)
                    }
                )
            }
        }
    }
}

@Composable
private fun FlagChip(flag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = flag, fontSize = 20.sp)
    }
}

@Composable
fun VolumeSliderRow(volume: Float, onVolumeChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (volume == 0f) "🔇" else "🔊",
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.width(8.dp))

        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
    }
}