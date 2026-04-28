package com.aau.saboteur.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.PlacedTunnelCard
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.ui.toContentDescription
import com.aau.saboteur.ui.toDrawableName
import kotlin.math.hypot

private const val BoardColumns = 13
private const val BoardRows = 9
private const val BackgroundGridColumns = (BoardColumns + 1) * 3
private const val BackgroundGridRows = (BoardRows + 1) * 3
private const val BoardCardWidthDp = 86
private const val BoardCardHeightDp = 126
private const val BoardCardSpacingDp = 0
private const val BoardContentWidthDp = BoardColumns * BoardCardWidthDp + (BoardColumns - 1) * BoardCardSpacingDp
private const val BoardContentHeightDp = BoardRows * BoardCardHeightDp + (BoardRows - 1) * BoardCardSpacingDp
private const val BoardGridLineAlpha = 0.28f
private const val BoardSurfaceAlpha = 0.86f
private const val MinBoardZoom = 0.75f
private const val MaxBoardZoom = 2.0f
private val BoardOuterPadding = 14.dp
private val BoardMinHeight = 320.dp
private val BoardDefaultHeight = 520.dp
private val BoardTonalElevation = 6.dp
private val BoardShadowElevation = 12.dp
private val BoardSurfaceShape = RoundedCornerShape(28.dp)
private val BoardShape = RoundedCornerShape(18.dp)
private val TileElevation = 4.dp
private val TileBorderWidth = 2.dp
private val TileContentPadding = 6.dp

@Composable
fun BoardGrid(
    placements: List<PlacedTunnelCard>,
    modifier: Modifier = Modifier
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val placementMap = placements.associateBy(PlacedTunnelCard::position)
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = BoardGridLineAlpha)
    var scale by remember { mutableFloatStateOf(1f) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = BoardSurfaceAlpha),
        shape = BoardSurfaceShape,
        tonalElevation = BoardTonalElevation,
        shadowElevation = BoardShadowElevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BoardMinHeight, max = BoardDefaultHeight)
                .padding(BoardOuterPadding)
        ) {
            val scaledCardWidth  = (BoardCardWidthDp  * scale).dp
            val scaledCardHeight = (BoardCardHeightDp * scale).dp
            val scaledWidth      = (BoardContentWidthDp  * scale).dp
            val scaledHeight     = (BoardContentHeightDp * scale).dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            horizontalScroll.dispatchRawDelta(-dragAmount.x)
                            verticalScroll.dispatchRawDelta(-dragAmount.y)
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                val pressedChanges = event.changes.filter(PointerInputChange::pressed)
                                if (pressedChanges.size >= 2) {
                                    val previousDistance = pressedChanges.pointerDistance(usePreviousPosition = true)
                                    val currentDistance  = pressedChanges.pointerDistance(usePreviousPosition = false)
                                    if (previousDistance > 0f) {
                                        val zoomChange = currentDistance / previousDistance
                                        scale = (scale * zoomChange).coerceIn(MinBoardZoom, MaxBoardZoom)
                                    }
                                    pressedChanges.forEach(PointerInputChange::consume)
                                }
                            } while (event.changes.any(PointerInputChange::pressed))
                        }
                    }
                    .verticalScroll(verticalScroll, enabled = false)
                    .horizontalScroll(horizontalScroll, enabled = false)
            ) {
                // Tatsächliche Größe = skaliert → Scroll-Bereich wächst/schrumpft korrekt
                Box(modifier = Modifier.size(width = scaledWidth, height = scaledHeight)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val spacing = size.width / BackgroundGridColumns
                        repeat(BackgroundGridColumns + 1) { index ->
                            val x = spacing * index
                            drawLine(
                                color = lineColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        val rowSpacing = size.height / BackgroundGridRows
                        repeat(BackgroundGridRows + 1) { index ->
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
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(BoardCardSpacingDp.dp)
                    ) {
                        repeat(BoardRows) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(BoardCardSpacingDp.dp)) {
                                repeat(BoardColumns) { column ->
                                    val placement = placementMap[BoardPosition(row = row, column = column)]
                                    BoardTile(
                                        card = placement?.card,
                                        cardWidth = scaledCardWidth,
                                        cardHeight = scaledCardHeight
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardTile(
    card: TunnelCard?,
    cardWidth: androidx.compose.ui.unit.Dp = BoardCardWidthDp.dp,
    cardHeight: androidx.compose.ui.unit.Dp = BoardCardHeightDp.dp,
) {
    val context = LocalContext.current
    val drawableName = card?.toDrawableName()
    @Suppress("DiscouragedApi")
    val imageRes = drawableName?.let {
        context.resources.getIdentifier(it, "drawable", context.packageName)
    } ?: 0

    Card(
        modifier = Modifier.size(width = cardWidth, height = cardHeight),
        shape = BoardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = TileElevation),
        colors = CardDefaults.cardColors(containerColor = tileColor(card))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(TileBorderWidth, tileBorderColor(card), BoardShape)
                .padding(TileContentPadding),
            contentAlignment = Alignment.Center
        ) {
            when {
                card == null -> EmptyTilePattern()
                card.type == CardType.GOAL && !card.isRevealed -> HiddenGoalCard()
                imageRes != 0 -> {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = card.toContentDescription(),
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

private fun List<PointerInputChange>.pointerDistance(usePreviousPosition: Boolean): Float {
    val firstPosition  = if (usePreviousPosition) this[0].previousPosition else this[0].position
    val secondPosition = if (usePreviousPosition) this[1].previousPosition else this[1].position
    return hypot(
        x = secondPosition.x - firstPosition.x,
        y = secondPosition.y - firstPosition.y
    )
}
