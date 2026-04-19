package com.aau.saboteur.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

    val context = LocalContext.current
    val drawableName = card.toDrawableName()
    val resId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(60.dp)
                .height(90.dp)
                .graphicsLayer { rotationZ = rotation }
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
        ) {
            if (resId != 0) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = card.toContentDescription(),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.width(60.dp).height(90.dp)
                )
            } else {
                Text(
                    text = drawableName,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Button(
            onClick = {
                isRotated = !isRotated
                onRotationChanged(isRotated)
            },
            modifier = Modifier.width(60.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text("Drehen", fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
