package com.aau.se2game.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard

@Composable
fun TunnelCardView(
    card: TunnelCard,
    onRotationChanged: (isRotated: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isRotated by remember { mutableStateOf(card.isRotated) }

    val rotation by animateFloatAsState(
        targetValue = if (isRotated) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "cardRotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer { rotationZ = rotation }
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = card.type.name,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = card.connections.joinToString { it.name.first().toString() },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Button(onClick = {
            isRotated = !isRotated
            onRotationChanged(isRotated)
        }) {
            Text("Drehen")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TunnelCardViewPreview() {
    TunnelCardView(
        card = TunnelCard(
            id = "preview",
            type = CardType.PATH,
            connections = setOf(Direction.TOP, Direction.BOTTOM)
        )
    )
}