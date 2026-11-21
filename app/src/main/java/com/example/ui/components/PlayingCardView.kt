package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.Card
import com.example.domain.RummyEngine
import com.example.ui.theme.CardBackCrimson
import com.example.ui.theme.CardBackCrimsonDark
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardIvory
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.SkyHighlight
import com.example.ui.theme.SuitCrimson
import com.example.ui.theme.SuitInk

@Composable
fun PlayingCardView(
    card: Card,
    wildRank: Int,
    isSelected: Boolean = false,
    isNew: Boolean = false,
    width: Dp = 50.dp,
    height: Dp = 74.dp,
    modifier: Modifier = Modifier
) {
    val riseOffset by animateDpAsState(
        targetValue = if (isSelected) (-14).dp else 0.dp,
        animationSpec = spring(),
        label = "cardRise"
    )
    val isWild = RummyEngine.isWildCard(card, wildRank)
    val textColor = when {
        card.isPrintedJoker -> Color(0xFF8F1D2C)
        card.isRed -> SuitCrimson
        else -> SuitInk
    }

    val borderCol = when {
        isSelected -> ChampagneGold
        isNew -> SkyHighlight
        card.isPrintedJoker -> Color(0xFFD4A134)
        else -> CardBorder
    }

    val bgBrush = if (card.isPrintedJoker) {
        Brush.linearGradient(listOf(Color(0xFFFFFDF6), Color(0xFFF6E4B0)))
    } else {
        Brush.linearGradient(listOf(CardIvory, Color(0xFFF3ECE0)))
    }

    Box(
        modifier = modifier
            .offset(y = riseOffset)
            .size(width = width, height = height)
            .shadow(if (isSelected) 10.dp else 4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(bgBrush)
            .border(if (isSelected || isNew) 2.dp else 1.dp, borderCol, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        // Top-left corner index (always crisp and readable in 14-card fan!)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = card.rankLabel,
                color = textColor,
                fontFamily = JetBrainsMonoFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (width.value * 0.27f).sp,
                lineHeight = (width.value * 0.28f).sp
            )
            Text(
                text = card.suit.symbol,
                color = textColor,
                fontSize = (width.value * 0.24f).sp,
                lineHeight = (width.value * 0.24f).sp
            )
        }

        // Center large suit pip
        Text(
            text = if (card.isPrintedJoker) "★" else card.suit.symbol,
            color = textColor.copy(alpha = 0.88f),
            fontSize = (width.value * 0.42f).sp,
            modifier = Modifier.align(Alignment.Center)
        )

        if (isWild) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ChampagneGold)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "W",
                    color = SuitInk,
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 8.sp
                )
            }
        }

        if (isNew) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SkyHighlight)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "NEW",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 7.sp
                )
            }
        }
    }
}

@Composable
fun CardBackView(
    count: Int? = null,
    width: Dp = 50.dp,
    height: Dp = 74.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .shadow(6.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(CardBackCrimson, CardBackCrimsonDark)))
            .border(1.5.dp, ChampagneGold, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(4.dp)
        ) {
            Text(text = "★", color = ChampagneGold, fontSize = 18.sp)
            if (count != null) {
                Text(
                    text = count.toString(),
                    color = Color.White,
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
