package com.aau.se2game

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.aau.se2game.ui.theme.SE2GameTheme
import com.aau.se2game.ui.board.SaboteurBoardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SE2GameTheme {
                SaboteurBoardScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SaboteurBoardPreview() {
    SE2GameTheme {
        SaboteurBoardScreen()
    }
}
