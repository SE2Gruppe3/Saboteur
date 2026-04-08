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


@Composable
fun RoleCardView(
    role: Role,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(if (compact) 12.dp else 20.dp)
    
    val cardBrush = when (role) {
        Role.GOLDDIGGER -> Brush.linearGradient(
            colors = listOf(Quartz, OreGold, OreCopper)
        )
        Role.SABOTEUR -> Brush.linearGradient(
            colors = listOf(Color(0xFF8B0000), Color(0xFF4B0000))
        )
    }

    val textColor = when (role) {
        Role.GOLDDIGGER -> MineCoal
        Role.SABOTEUR -> Quartz
    }

    val verticalPadding = if (compact) 6.dp else 16.dp
    val horizontalPadding = if (compact) 12.dp else 20.dp
    val textStyle = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge

    Card(
        modifier = modifier.shadow(
            elevation = if (compact) 4.dp else 12.dp,
            shape = shape,
            ambientColor = if (role == Role.GOLDDIGGER) GlowGold else Color.Black,
            spotColor = if (role == Role.GOLDDIGGER) GlowGold else Color.Black
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = if (compact) 1.dp else 2.dp,
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
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = role.name,
                    color = textColor,
                    style = textStyle,
                    fontWeight = FontWeight.Bold
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

@Preview(showBackground = true)
@Composable
private fun RoleCardViewCompactPreview() {
    RoleCardView(role = Role.GOLDDIGGER, compact = true)
}
