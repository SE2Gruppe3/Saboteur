package com.aau.saboteur.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aau.saboteur.model.Role
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.ui.TunnelCardView

@Composable
fun PlayerHandRow(
    hand: List<TunnelCard>,
    role: Role?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        role?.let {
            RoleCardView(
                role = it,
                compact = true,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Text(
            text = "Your Hand",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            items(hand) { card ->
                TunnelCardView(card = card)
            }
        }
    }
}
