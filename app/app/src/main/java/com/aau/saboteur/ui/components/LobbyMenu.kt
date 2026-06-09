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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aau.saboteur.R
import com.aau.saboteur.util.LanguageManager
import com.aau.saboteur.util.rememberLocalizedContext

private val MenuWidth = 220.dp
private val MenuCornerRadius = 12.dp
private val FlagChipSize = 36.dp
private val FlagTextSize = 20.sp
private val SelectorPaddingH = 12.dp
private val SelectorPaddingV = 6.dp
private val FlagChipSpacing = 4.dp
private val VolumeIconSpacing = 8.dp

@Composable
fun LobbyMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onLeaveGame: (() -> Unit)? = null,
    showLeaveGame: Boolean = true,
    onShowSpielregeln: (() -> Unit)? = null
) {
    val language by LanguageManager.currentLanguage
    val localizedContext = rememberLocalizedContext(language)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(MenuWidth)
            .clip(RoundedCornerShape(MenuCornerRadius))
    ) {
        CompositionLocalProvider(LocalContext provides localizedContext) {
            LanguageSelector()

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            VolumeSliderRow(volume = volume, onVolumeChange = onVolumeChange)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text(stringResource(R.string.spielregeln_button)) },
                onClick = {
                    onShowSpielregeln?.invoke()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null
                    )
                }
            )

            if (showLeaveGame && onLeaveGame != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.leave_game), color = MaterialTheme.colorScheme.error) },
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
}

@Composable
fun LanguageSelector() {
    val context = LocalContext.current
    val selectedLanguage by LanguageManager.currentLanguage
    var showAlternative by remember { mutableStateOf(false) }

    val currentFlag     = if (selectedLanguage == LanguageManager.LANG_DE) "🇩🇪" else "🇬🇧"
    val alternativeFlag = if (selectedLanguage == LanguageManager.LANG_DE) "🇬🇧" else "🇩🇪"
    val alternativeLang = if (selectedLanguage == LanguageManager.LANG_DE) LanguageManager.LANG_EN else LanguageManager.LANG_DE

    Column(modifier = Modifier.padding(horizontal = SelectorPaddingH, vertical = SelectorPaddingV)) {
        FlagChip(flag = currentFlag, onClick = { showAlternative = !showAlternative })

        AnimatedVisibility(
            visible = showAlternative,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(FlagChipSpacing))
                FlagChip(
                    flag = alternativeFlag,
                    onClick = {
                        LanguageManager.setLanguage(context, alternativeLang)
                        showAlternative = false
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
            .size(FlagChipSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = flag, fontSize = FlagTextSize)
    }
}

@Composable
fun VolumeSliderRow(volume: Float, onVolumeChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = SelectorPaddingH, vertical = SelectorPaddingV)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (volume == 0f) "🔇" else "🔊",
            fontSize = FlagTextSize
        )

        Spacer(modifier = Modifier.width(VolumeIconSpacing))

        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
    }
}
