package com.aau.saboteur.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aau.saboteur.model.Role
import com.aau.saboteur.ui.theme.GlowGold
import com.aau.saboteur.ui.theme.MineCoal
import com.aau.saboteur.ui.theme.OreCopper
import com.aau.saboteur.ui.theme.OreGold
import com.aau.saboteur.ui.theme.Quartz
import com.aau.saboteur.ui.theme.Steel

@Composable
fun RoleCardView(
    role: Role,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    
    val cardBrush = when (role) {
        Role.GOLDDIGGER -> Brush.linearGradient(
            colors = listOf(Quartz, OreGold, OreCopper)
        )
        Role.SABOTEUR -> Brush.linearGradient(
            colors = listOf(Color(0xFF8B0000), Color(0xFF4B0000)) // Dark Red Gradient
        )
    }

    val textColor = when (role) {
        Role.GOLDDIGGER -> MineCoal
        Role.SABOTEUR -> Quartz
    }

    Card(
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = shape,
            ambientColor = if (role == Role.GOLDDIGGER) GlowGold else Color.Black,
            spotColor = if (role == Role.GOLDDIGGER) GlowGold else Color.Black
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 2.dp,
            color = if (role == Role.GOLDDIGGER) GlowGold else Color(0xFF8B0000)
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(shape)
                .background(cardBrush)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = role.name,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoleCardViewGoldPreview() {
    RoleCardView(role = Role.GOLDDIGGER)
}

@Preview(showBackground = true)
@Composable
private fun RoleCardViewSaboteurPreview() {
    RoleCardView(role = Role.SABOTEUR)
}
