package com.aau.se2game.ui.board

import com.aau.se2game.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val BoardColumns = 9
private const val BoardRows = 13
private const val RevealHiddenGoals = true
private const val CardWidthDp = 86
private const val CardHeightDp = 126
private const val CardSpacingDp = 12
private const val StartColumn = 4
private const val StartRow = 10

@Composable
fun SaboteurBoardScreen(modifier: Modifier = Modifier) {
    val board = remember { sampleBoard() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF120E09))
    ) {
        MineBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BoardHeader()
            BoardSurface(board = board)
        }
    }
}

@Composable
private fun BoardHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xCC2F2115),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Saboteur",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color(0xFFFFE6B8),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BoardSurface(board: List<List<GridCell>>) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0x99251710),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 8.dp,
        shadowElevation = 18.dp
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
                val cardWidthPx = with(density) { CardWidthDp.dp.roundToPx() }
                val cardHeightPx = with(density) { CardHeightDp.dp.roundToPx() }
                val spacingPx = with(density) { CardSpacingDp.dp.roundToPx() }
                val cellWidthPx = cardWidthPx + spacingPx
                val cellHeightPx = cardHeightPx + spacingPx
                val contentWidthPx =
                    BoardColumns * cardWidthPx + (BoardColumns - 1) * spacingPx
                val contentHeightPx =
                    BoardRows * cardHeightPx + (BoardRows - 1) * spacingPx
                val startCardCenterX = StartColumn * cellWidthPx + cardWidthPx / 2
                val startCardCenterY = StartRow * cellHeightPx + cardHeightPx / 2
                val targetX = (startCardCenterX - viewportWidthPx / 2).coerceIn(
                    0,
                    (contentWidthPx - viewportWidthPx).coerceAtLeast(0)
                )
                val targetY = (startCardCenterY - viewportHeightPx / 2).coerceIn(
                    0,
                    (contentHeightPx - viewportHeightPx).coerceAtLeast(0)
                )
                horizontalScroll.scrollTo(targetX)
                verticalScroll.scrollTo(targetY)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineColor = Color(0x33F8D9A0)
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
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                board.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { cell ->
                            GridCard(cell = cell)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCard(cell: GridCell) {
    val (cardColor, borderColor) = when (cell.type) {
        CellType.Start -> Pair(Color(0xFF2F6B55), Color(0xFFA5E0C5))
        CellType.Goal -> Pair(Color(0xFF4A3B19), Color(0xFFE1C16A))
        CellType.Path -> Pair(Color(0xFF585A67), Color(0xFFC6CCD8))
        CellType.Empty -> Pair(Color(0xFF241A13), Color(0xFF46362A))
    }

    Card(
        modifier = Modifier.size(width = CardWidthDp.dp, height = CardHeightDp.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, borderColor, RoundedCornerShape(18.dp))
        ) {
            if (cell.type == CellType.Empty) {
                Box(modifier = Modifier.padding(10.dp)) {
                    EmptyCellPattern()
                }
            } else if (cell.isFaceDown && !RevealHiddenGoals) {
                FaceDownGoalCard()
            } else if (cell.imageRes != null) {
                Image(
                    painter = painterResource(cell.imageRes),
                    contentDescription = cell.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(modifier = Modifier.padding(10.dp)) {
                    EmptyCellPattern()
                }
            }
        }
    }
}

@Composable
private fun FaceDownGoalCard() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1E1E23), Color(0xFF353744), Color(0xFF23252E))
            )
        )
        drawRect(
            color = Color(0x66C7AC72),
            topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
            size = Size(size.width * 0.84f, size.height * 0.84f),
            style = Stroke(width = 2.dp.toPx())
        )
        repeat(5) { index ->
            val y = size.height * (0.16f + index * 0.14f)
            drawLine(
                color = Color(0x33E1C98C),
                start = Offset(size.width * 0.14f, y),
                end = Offset(size.width * 0.86f, y),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun EmptyCellPattern() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0x662C2118), Color(0x22120805)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
        drawRect(
            color = Color(0x552B2016),
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            ),
            topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
            size = Size(size.width * 0.8f, size.height * 0.62f)
        )
    }
}

@Composable
private fun MineBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF17120E),
                    Color(0xFF241A13),
                    Color(0xFF302116)
                )
            )
        )
    }
}

private data class GridCell(
    val label: String,
    val type: CellType,
    val imageRes: Int? = null,
    val isFaceDown: Boolean = false
)

private enum class CellType {
    Start,
    Goal,
    Path,
    Empty
}

private fun sampleBoard(): List<List<GridCell>> {
    val board = MutableList(BoardRows) {
        MutableList(BoardColumns) {
            GridCell(label = "OPEN", type = CellType.Empty)
        }
    }

    val hiddenGoals = listOf(
        R.drawable.goal_gold,
        R.drawable.goal_stone1,
        R.drawable.goal_stone2
    ).shuffled()

    board[0][2] = GridCell("GOAL", CellType.Goal, imageRes = hiddenGoals[0], isFaceDown = true)
    board[0][4] = GridCell("GOAL", CellType.Goal, imageRes = hiddenGoals[1], isFaceDown = true)
    board[0][6] = GridCell("GOAL", CellType.Goal, imageRes = hiddenGoals[2], isFaceDown = true)

    board[StartRow][StartColumn] = GridCell("START", CellType.Start, imageRes = R.drawable.startkarte)

    board[9][4] = GridCell("PATH", CellType.Path, imageRes = R.drawable.path_tb)
    board[8][4] = GridCell("PATH", CellType.Path, imageRes = R.drawable.path_tlb)
    board[7][4] = GridCell("PATH", CellType.Path, imageRes = R.drawable.path_tlr)
    board[7][3] = GridCell("PATH", CellType.Path, imageRes = R.drawable.path_tr)
    board[6][3] = GridCell("PATH", CellType.Path, imageRes = R.drawable.path_tb)
    board[5][3] = GridCell("PATH", CellType.Path, imageRes = R.drawable.path_tl)
    board[5][2] = GridCell("PATH", CellType.Path, imageRes = R.drawable.path_tlr)
    board[4][2] = GridCell("PATH", CellType.Path, imageRes = R.drawable.path_tb)

    return board
}
