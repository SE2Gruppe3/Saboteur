package com.aau.saboteur.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.PlacedTunnelCard
import com.aau.saboteur.model.TunnelCard

private const val BoardColumns = 9
private const val BoardRows = 13
private const val BoardCardWidthDp = 86
private const val BoardCardHeightDp = 126
private const val BoardCardSpacingDp = 12
private const val MinBoardZoom = 0.75f
private const val MaxBoardZoom = 2.0f
private val BoardShape = RoundedCornerShape(18.dp)

@Composable
fun BoardGrid(
    placements: List<PlacedTunnelCard>,
    startPosition: BoardPosition,
    modifier: Modifier = Modifier
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val placementMap = placements.associateBy(PlacedTunnelCard::position)
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    var scale by remember { mutableFloatStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(MinBoardZoom, MaxBoardZoom)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .padding(14.dp)
        ) {
            val density = LocalDensity.current
            val viewportWidthPx = constraints.maxWidth
            val viewportHeightPx = constraints.maxHeight

            LaunchedEffect(viewportWidthPx, viewportHeightPx) {
                val cardWidthPx = with(density) { BoardCardWidthDp.dp.roundToPx() }
                val cardHeightPx = with(density) { BoardCardHeightDp.dp.roundToPx() }
                val spacingPx = with(density) { BoardCardSpacingDp.dp.roundToPx() }
                val cellWidthPx = cardWidthPx + spacingPx
                val cellHeightPx = cardHeightPx + spacingPx
                val contentWidthPx = BoardColumns * cardWidthPx + (BoardColumns - 1) * spacingPx
                val contentHeightPx = BoardRows * cardHeightPx + (BoardRows - 1) * spacingPx
                val startCenterX = startPosition.column * cellWidthPx + cardWidthPx / 2
                val startCenterY = startPosition.row * cellHeightPx + cardHeightPx / 2
                val targetX = (startCenterX - viewportWidthPx / 2).coerceIn(
                    0,
                    (contentWidthPx - viewportWidthPx).coerceAtLeast(0)
                )
                val targetY = (startCenterY - viewportHeightPx / 2).coerceIn(
                    0,
                    (contentHeightPx - viewportHeightPx).coerceAtLeast(0)
                )
                horizontalScroll.scrollTo(targetX)
                verticalScroll.scrollTo(targetY)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val spacing = size.width / (BoardColumns + 1)
                repeat(BoardColumns + 2) { index ->
                    val x = spacing * index
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                val rowSpacing = size.height / (BoardRows + 1)
                repeat(BoardRows + 2) { index ->
                    val y = rowSpacing * index
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .transformable(state = transformableState)
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll),
                verticalArrangement = Arrangement.spacedBy(BoardCardSpacingDp.dp)
            ) {
                repeat(BoardRows) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(BoardCardSpacingDp.dp)) {
                        repeat(BoardColumns) { column ->
                            val placement = placementMap[BoardPosition(row = row, column = column)]
                            BoardTile(card = placement?.card)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardTile(card: TunnelCard?) {
    val context = LocalContext.current
    val drawableName = card?.toDrawableName()
    val imageRes = drawableName?.let {
        context.resources.getIdentifier(it, "drawable", context.packageName)
    } ?: 0

    Card(
        modifier = Modifier.size(width = BoardCardWidthDp.dp, height = BoardCardHeightDp.dp),
        shape = BoardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = tileColor(card))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, tileBorderColor(card), BoardShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                card == null -> EmptyTilePattern()
                card.type == CardType.GOAL && !card.isRevealed -> HiddenGoalCard()
                imageRes != 0 -> {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = drawableName,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = if (card.isRotated) 180f else 0f
                            }
                    )
                }
                else -> ConnectionPattern(card = card)
            }
        }
    }
}

@Composable
private fun HiddenGoalCard() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        HiddenGoalPattern()
        Text(
            text = "?",
            color = Color(0xFFF4D35E),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun EmptyTilePattern() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0x33241812), Color(0x11110C08)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
    }
}

@Composable
private fun HiddenGoalPattern() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF2F2A26), Color(0xFF1E1A17))
            )
        )
    }
}

@Composable
private fun ConnectionPattern(card: TunnelCard) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 11.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val north = Offset(center.x, size.height * 0.12f)
        val south = Offset(center.x, size.height * 0.88f)
        val west = Offset(size.width * 0.12f, center.y)
        val east = Offset(size.width * 0.88f, center.y)
        val lineColor = when (card.type) {
            CardType.START -> Color(0xFFF5F1E8)
            CardType.GOAL -> Color(0xFFF4D35E)
            else -> Color(0xFFF1E3C8)
        }

        if (Direction.TOP in card.connections) {
            drawLine(lineColor, center, north, strokeWidth = strokeWidth)
        }
        if (Direction.BOTTOM in card.connections) {
            drawLine(lineColor, center, south, strokeWidth = strokeWidth)
        }
        if (Direction.LEFT in card.connections) {
            drawLine(lineColor, center, west, strokeWidth = strokeWidth)
        }
        if (Direction.RIGHT in card.connections) {
            drawLine(lineColor, center, east, strokeWidth = strokeWidth)
        }

        drawCircle(
            color = lineColor.copy(alpha = 0.24f),
            radius = size.minDimension * 0.18f,
            center = center
        )
        drawCircle(
            color = lineColor,
            radius = size.minDimension * 0.07f,
            center = center
        )
    }
}

private fun tileColor(card: TunnelCard?): Color = when {
    card == null -> Color(0xFF231914)
    card.type == CardType.START -> Color(0xFF416A43)
    card.type == CardType.GOAL && !card.isRevealed -> Color(0xFF2A211A)
    card.type == CardType.GOAL -> Color(0xFF6E5524)
    else -> Color(0xFF5C616D)
}

private fun tileBorderColor(card: TunnelCard?): Color = when {
    card == null -> Color(0xFF4A3A2C)
    card.type == CardType.START -> Color(0xFFA7D6A2)
    card.type == CardType.GOAL -> Color(0xFFE7C67A)
    else -> Color(0xFFC8D0DB)
}

private fun TunnelCard.toDrawableName(): String = when (type) {
    CardType.START -> "start"
    CardType.GOAL -> when (id) {
        "goal_gold" -> "goal_gold"
        "goal_stone_1" -> "goal_stone1"
        "goal_stone_2" -> "goal_stone2"
        else -> "goal_stone1"
    }
    else -> {
        val prefix = if (type == CardType.PATH) "path" else "dead"
        if (connections.size == 4) {
            "${prefix}_cross"
        } else {
            val suffix = buildString {
                if (Direction.TOP in connections) append('t')
                if (Direction.LEFT in connections) append('l')
                if (Direction.RIGHT in connections) append('r')
                if (Direction.BOTTOM in connections) append('b')
            }
            "${prefix}_$suffix"
        }
    }
}
