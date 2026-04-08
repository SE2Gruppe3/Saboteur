package com.aau.saboteur.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.ui.TunnelCardView

@Composable
fun PlayerHandRow(
    hand: List<TunnelCard>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        items(hand) { card ->
            TunnelCardView(card = card)
        }
    }
}
