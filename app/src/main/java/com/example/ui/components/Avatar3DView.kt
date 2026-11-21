package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChampagneGold

private val avatarPalettes = listOf(
    Color(0xFFF59E0B) to Color(0xFF78350F),
    Color(0xFF10B981) to Color(0xFF064E3B),
    Color(0xFF38BDF8) to Color(0xFF0C4A6E),
    Color(0xFFF43F5E) to Color(0xFF881337),
    Color(0xFFA855F7) to Color(0xFF4C1D95),
    Color(0xFFEAB308) to Color(0xFF713F12)
)

@Composable
fun Avatar3DView(
    avatarId: Int,
    name: String,
    size: Dp = 44.dp,
    isActiveTurn: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (lightCol, darkCol) = avatarPalettes[kotlin.math.abs(avatarId) % avatarPalettes.size]
    val scale by animateFloatAsState(
        targetValue = if (isActiveTurn) 1.08f else 1f,
        animationSpec = spring(),
        label = "avatarScale"
    )
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "V"

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .border(
                width = if (isActiveTurn) 2.5.dp else 1.5.dp,
                color = if (isActiveTurn) ChampagneGold else ChampagneGold.copy(alpha = 0.55f),
                shape = CircleShape
            )
            .background(darkCol),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lightCol, darkCol),
                    center = Offset(this.size.width * 0.34f, this.size.height * 0.28f),
                    radius = this.size.width * 0.85f
                )
            )
            // Subtle 3D silhouette head + shoulders
            drawCircle(
                color = Color.White.copy(alpha = 0.86f),
                radius = this.size.width * 0.19f,
                center = Offset(this.size.width * 0.5f, this.size.height * 0.38f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.78f),
                radius = this.size.width * 0.34f,
                center = Offset(this.size.width * 0.5f, this.size.height * 0.86f)
            )
            // Specular gloss highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(this.size.width * 0.28f, this.size.height * 0.22f),
                    radius = this.size.width * 0.42f
                )
            )
        }
        Text(
            text = initial,
            color = darkCol,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.24f).sp
        )
    }
}
