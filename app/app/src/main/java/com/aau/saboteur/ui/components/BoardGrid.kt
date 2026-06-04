package com.aau.saboteur.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.PlacedTunnelCard
import com.aau.saboteur.model.TunnelCard
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import com.aau.saboteur.ui.toCanonicalDrawableName
import com.aau.saboteur.ui.toContentDescription
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

private const val BoardColumns = 13
private const val BoardRows = 9
private const val ExtraPaddingTiles = 4
private const val BoardCardWidthDp = 86
private const val BoardCardHeightDp = 126
private const val BoardCardSpacingDp = 0
private const val BoardContentWidthDp = BoardColumns * BoardCardWidthDp + (BoardColumns - 1) * BoardCardSpacingDp
private const val BoardContentHeightDp = BoardRows * BoardCardHeightDp + (BoardRows - 1) * BoardCardSpacingDp
private const val BoardGridLineAlpha = 0.12f
private const val MinBoardZoom = 0.5f
private const val MaxBoardZoom = 2.0f
private val BoardOuterPadding = 0.dp
private val BoardShape = RoundedCornerShape(8.dp)
private val TileElevation = 4.dp
private val TileBorderWidth = 2.dp
private val TileContentPadding = 6.dp

/**
 * Scrollbares, zoombares Spielfeld für Saboteur.
 *
 * Zeigt ein [BoardColumns]×[BoardRows]-Raster aus [BoardTile]-Kacheln.
 *
 * @param validPositions cells highlighted as legal placement targets for the selected card
 */
@Composable
fun BoardGrid(
    placements: List<PlacedTunnelCard>,
    modifier: Modifier = Modifier,
    validPositions: List<BoardPosition> = emptyList(),
    onCellClick: (BoardPosition) -> Unit = {},
) {
    var isFirstLoad by rememberSaveable { mutableStateOf(true) }
    var savedScrollX by rememberSaveable { mutableIntStateOf(0) }
    var savedScrollY by rememberSaveable { mutableIntStateOf(0) }
    var scale by rememberSaveable { mutableFloatStateOf(0.8f) }

    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val placementMap = placements.associateBy(PlacedTunnelCard::position)
    val validPositionSet = remember(validPositions) { validPositions.toHashSet() }
    val gridColor = Color(0xFF000000).copy(alpha = BoardGridLineAlpha)

    LaunchedEffect(Unit) {
        if (isFirstLoad) {
            horizontalScroll.scrollTo(horizontalScroll.maxValue / 2)
            verticalScroll.scrollTo(verticalScroll.maxValue / 2)
            isFirstLoad = false
        } else {
            horizontalScroll.scrollTo(savedScrollX)
            verticalScroll.scrollTo(savedScrollY)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { horizontalScroll.value }.collect { savedScrollX = it }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { verticalScroll.value }.collect { savedScrollY = it }
    }

    Surface(
        modifier = modifier,
        color = Color(0xFF1A1614) // Deep earth base color
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(BoardOuterPadding)
        ) {
            val scaledCardWidth  = (BoardCardWidthDp  * scale).dp
            val scaledCardHeight = (BoardCardHeightDp * scale).dp
            
            val scaledWidth      = (BoardContentWidthDp  * scale).dp
            val scaledHeight     = (BoardContentHeightDp * scale).dp

            val extraWidth = (ExtraPaddingTiles * 2 * BoardCardWidthDp * scale).dp
            val extraHeight = (ExtraPaddingTiles * 2 * BoardCardHeightDp * scale).dp
            
            val totalWidth = scaledWidth + extraWidth
            val totalHeight = scaledHeight + extraHeight

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            // 1. Dämpfung für Zoom anwenden (Sensor-Rauschen filtern)
                            val smoothedZoom = 1f + (zoom - 1f) * 0.8f
                            
                            val oldScale = scale
                            val newScale = (scale * smoothedZoom).coerceIn(MinBoardZoom, MaxBoardZoom)
                            
                            if (oldScale != newScale) {
                                // 2. Pivot-Berechnung für Zoom:
                                val scaleFactor = newScale / oldScale
                                val deltaX = (horizontalScroll.value + centroid.x) * (scaleFactor - 1f)
                                val deltaY = (verticalScroll.value + centroid.y) * (scaleFactor - 1f)
                                
                                scale = newScale
                                horizontalScroll.dispatchRawDelta(deltaX)
                                verticalScroll.dispatchRawDelta(deltaY)
                            }

                            // 3. Pan/Drag Handling
                            horizontalScroll.dispatchRawDelta(-pan.x)
                            verticalScroll.dispatchRawDelta(-pan.y)
                        }
                    }
                    .verticalScroll(verticalScroll, enabled = false)
                    .horizontalScroll(horizontalScroll, enabled = false)
            ) {
                Box(
                    modifier = Modifier.size(width = totalWidth, height = totalHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRubbleBackground()
                        
                        val gridCols = (BoardColumns + 2 * ExtraPaddingTiles + 1) * 3
                        val gridRows = (BoardRows + 2 * ExtraPaddingTiles + 1) * 3
                        
                        val spacing = size.width / gridCols
                        repeat(gridCols + 1) { index ->
                            val x = spacing * index
                            drawLine(
                                color = gridColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        val rowSpacing = size.height / gridRows
                        repeat(gridRows + 1) { index ->
                            val y = rowSpacing * index
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    Column(modifier = Modifier.size(width = scaledWidth, height = scaledHeight)) {
                        repeat(BoardRows) { row ->
                            Row {
                                repeat(BoardColumns) { column ->
                                    val position = BoardPosition(row = row, column = column)
                                    val placement = placementMap[position]
                                    Box(modifier = Modifier.size(width = scaledCardWidth, height = scaledCardHeight)) {
                                        BoardTile(
                                            card = placement?.card,
                                            cardWidth = scaledCardWidth,
                                            cardHeight = scaledCardHeight,
                                            onClick = { onCellClick(position) }
                                        )
                                        if (position in validPositionSet) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Green.copy(alpha = 0.15f))
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
    }
}

private fun DrawScope.drawRubbleBackground() {
    val random = Random(42) // Fixed seed for stability
    
    // Background gradient for depth
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF241F1C), Color(0xFF14110F)),
            center = Offset(size.width / 2, size.height / 2),
            radius = size.maxDimension
        )
    )

    val stoneColors = listOf(
        Color(0xFF2D2622),
        Color(0xFF241E1A),
        Color(0xFF352D28),
        Color(0xFF1E1A17)
    )
    val highlightColor = Color(0xFF423A34)

    // Draw stone shapes
    repeat(400) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val w = random.nextFloat() * 100f + 20f
        val h = random.nextFloat() * 80f + 20f
        val rotation = random.nextFloat() * 360f
        
        val stonePath = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.8f, h * 0.1f)
            lineTo(w, h * 0.6f)
            lineTo(w * 0.4f, h)
            lineTo(w * 0.1f, h * 0.8f)
            close()
        }

        withTransform({
            translate(x, y)
            rotate(rotation, Offset.Zero)
        }) {
            // Shadow
            drawPath(
                path = stonePath,
                color = Color.Black,
                alpha = 0.4f
            )
            // Stone
            drawPath(
                path = stonePath,
                color = stoneColors[random.nextInt(stoneColors.size)],
                alpha = 0.7f
            )
            // Tiny highlight on one edge
            drawLine(
                color = highlightColor,
                start = Offset(0f, 0f),
                end = Offset(w * 0.3f, h * 0.05f),
                strokeWidth = 2f,
                alpha = 0.3f
            )
        }
    }

    // Cracks and crevices
    repeat(150) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val length = random.nextFloat() * 120f + 30f
        val angle = (random.nextFloat() * 2 * PI).toFloat()
        
        drawLine(
            color = Color(0xFF080605),
            start = Offset(x, y),
            end = Offset(
                x + cos(angle) * length,
                y + sin(angle) * length
            ),
            strokeWidth = 1.2f,
            alpha = 0.5f
        )
    }
}

@Composable
private fun BoardTile(
    card: TunnelCard?,
    cardWidth: Dp = BoardCardWidthDp.dp,
    cardHeight: Dp = BoardCardHeightDp.dp,
    onClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scaleAnim = remember { Animatable(1f) }
    val prevCard = remember { mutableStateOf<TunnelCard?>(card) }

    LaunchedEffect(card) {
        val wasNull = prevCard.value == null
        prevCard.value = card
        if (wasNull && card != null) {
            scaleAnim.snapTo(0.4f)
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            )
        }
    }

    Card(
        modifier = Modifier
            .size(width = cardWidth, height = cardHeight)
            .scale(scaleAnim.value)
            .pointerInput(onClick) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation()
                    if (up != null && currentEvent.changes.size == 1) {
                        onClick()
                        up.consume()
                    }
                }
            },
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
            AnimatedContent(
                targetState = card,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
                label = "tileContent"
            ) { displayedCard ->
                val drawableName = displayedCard?.toCanonicalDrawableName()
                @Suppress("DiscouragedApi")
                val imageRes = drawableName?.let {
                    context.resources.getIdentifier(it, "drawable", context.packageName)
                } ?: 0

                when {
                    displayedCard == null -> EmptyTilePattern()
                    displayedCard.type == CardType.GOAL && !displayedCard.isRevealed -> HiddenGoalCard()
                    imageRes != 0 -> {
                        Image(
                            painter = painterResource(id = imageRes),
                            contentDescription = displayedCard.toContentDescription(),
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                                .then(if (displayedCard.isRotated) Modifier.rotate(180f) else Modifier)
                        )
                    }
                    else -> ConnectionPattern(card = displayedCard)
                }
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
        // Show the rubble background through empty slots
        drawRect(
            color = Color.Black,
            alpha = 0.15f
        )
        
        val random = Random(42)
        repeat(3) {
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = random.nextFloat() * 10f,
                center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
            )
        }
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
    card == null -> Color.Transparent // Let the rubble background show through
    card.type == CardType.START -> Color(0xFF416A43)
    card.type == CardType.GOAL && !card.isRevealed -> Color(0xFF2A211A)
    card.type == CardType.GOAL -> Color(0xFF6E5524)
    else -> Color(0xFF5C616D)
}

private fun tileBorderColor(card: TunnelCard?): Color = when {
    card == null -> Color(0xFF4A3A2C).copy(alpha = 0.3f)
    card.type == CardType.START -> Color(0xFFA7D6A2)
    card.type == CardType.GOAL -> Color(0xFFE7C67A)
    else -> Color(0xFFC8D0DB)
}
