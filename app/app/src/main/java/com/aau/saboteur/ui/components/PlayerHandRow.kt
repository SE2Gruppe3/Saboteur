package com.aau.saboteur.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.ui.TunnelCardView
import kotlinx.coroutines.delay

@Composable
fun PlayerHandRow(
    hand: List<TunnelCard>,
    selectedCardId: String? = null,
    onCardSelected: (TunnelCard) -> Unit = {},
    onCardRotated: (TunnelCard, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val displayedCards = remember { mutableStateListOf<TunnelCard>() }

    LaunchedEffect(hand) {
        val handIds = hand.map { it.id }.toSet()
        hand.forEach { card ->
            if (displayedCards.none { it.id == card.id }) displayedCards.add(card)
        }
        val toRemove = displayedCards.filter { it.id !in handIds }
        if (toRemove.isNotEmpty()) {
            delay(350)
            displayedCards.removeAll { it.id !in handIds }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom
    ) {
        displayedCards.forEach { card ->
            key(card.id) {
                AnimatedVisibility(
                    visible = hand.any { it.id == card.id },
                    exit = slideOutVertically(
                        animationSpec = tween(300),
                        targetOffsetY = { it }
                    ) + fadeOut(animationSpec = tween(300))
                ) {
                    TunnelCardView(
                        card = card,
                        isSelected = card.id == selectedCardId,
                        onCardSelected = onCardSelected,
                        onRotationChanged = onCardRotated
                    )
                }
            }
        }
    }
}