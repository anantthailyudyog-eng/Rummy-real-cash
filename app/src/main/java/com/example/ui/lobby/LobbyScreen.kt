package com.example.ui.lobby

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.GameMode
import com.example.i18n.AppLanguage
import com.example.ui.VelvetUiState
import com.example.ui.components.Avatar3DView
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldCta
import com.example.ui.theme.EmeraldCtaBright
import com.example.ui.theme.GoldDark
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SuitInk
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.VelvetSurface
import com.example.ui.theme.VelvetSurfaceElevated
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LobbyScreen(
    uiState: VelvetUiState,
    onToggleMute: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleLanguage: () -> Unit,
    onClaimDailyBonus: () -> Unit,
    onSelectMode: (GameMode) -> Unit,
    onSelectSeats: (Int) -> Unit,
    onPlayNow: () -> Unit,
    onCpuPractice: () -> Unit
) {
    val s = uiState.strings
    val chipFormatted = NumberFormat.getIntegerInstance(Locale.US).format(uiState.profile.chips)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF102A1F), ObsidianBg)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top bar: Title + Audio/Voice/Language controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.brandTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = ChampagneGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = s.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LobbyPillButton(
                        active = !uiState.isMuted,
                        onClick = onToggleMute,
                        modifier = Modifier.testTag("lobby_sound_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (uiState.isMuted) s.soundOff else s.soundOn,
                            tint = if (uiState.isMuted) TextMuted else ChampagneGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    LobbyPillButton(
                        active = uiState.isVoiceEnabled,
                        onClick = onToggleVoice,
                        modifier = Modifier.testTag("lobby_voice_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = if (uiState.isVoiceEnabled) s.voiceOn else s.voiceOff,
                            tint = if (uiState.isVoiceEnabled) ChampagneGold else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    LobbyPillButton(
                        active = true,
                        onClick = onToggleLanguage,
                        modifier = Modifier.testTag("lobby_lang_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = "Language",
                            tint = ChampagneGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (uiState.language == AppLanguage.EN) "EN/हिं" else "हिं/EN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ChampagneGold
                        )
                    }
                }
            }

            // Hero Banner + Guest Avatar + Chips + Daily Bonus
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, ChampagneGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_lobby_banner),
                    contentDescription = "Velvet Rummy Den Lounge",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.18f), Color(0xE808140F))
                            )
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Avatar3DView(
                                avatarId = uiState.profile.avatarId,
                                name = uiState.profile.name,
                                size = 50.dp,
                                isActiveTurn = true
                            )
                            Column {
                                Text(
                                    text = uiState.profile.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "🪙 $chipFormatted ${s.practiceChips}",
                                    fontFamily = JetBrainsMonoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ChampagneGold
                                )
                            }
                        }

                        Button(
                            onClick = onClaimDailyBonus,
                            enabled = uiState.canClaimBonus,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChampagneGold,
                                contentColor = SuitInk,
                                disabledContainerColor = Color.White.copy(alpha = 0.12f),
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("daily_bonus_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CardGiftcard,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (uiState.canClaimBonus) s.dailyBonus else s.bonusClaimed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Format Selector (Points, Pool 101, Pool 201, Deals 3, Deals 6)
            Surface(
                color = VelvetSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = s.selectFormat.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                    val modeItems = listOf(
                        Triple(GameMode.POINTS, s.pointsRummy, s.pointsSub),
                        Triple(GameMode.POOL_101, s.pool101, s.pool101Sub),
                        Triple(GameMode.POOL_201, s.pool201, s.pool201Sub),
                        Triple(GameMode.DEALS_3, s.deals3, s.deals3Sub),
                        Triple(GameMode.DEALS_6, s.deals6, s.deals6Sub)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        modeItems.forEach { (mode, title, sub) ->
                            val selected = uiState.selectedMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) EmeraldCta.copy(alpha = 0.22f) else VelvetSurfaceElevated
                                    )
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) EmeraldCtaBright else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onSelectMode(mode) }
                                    .padding(12.dp)
                                    .testTag("mode_${mode.apiCode}")
                            ) {
                                Column {
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (selected) Color.White else TextLight
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = sub,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Table Capacity (2 to 6 seats)
            Surface(
                color = VelvetSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = s.tableCapacity.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (2..6).forEach { count ->
                            val selected = uiState.selectedSeats == count
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) ChampagneGold else VelvetSurfaceElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) GoldDark else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSelectSeats(count) }
                                    .testTag("seats_$count"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = count.toString(),
                                    fontFamily = JetBrainsMonoFont,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = if (selected) SuitInk else TextLight
                                )
                            }
                        }
                    }
                }
            }

            // Prominent green Play Now CTA
            Button(
                onClick = onPlayNow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldCta,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("play_now_button")
            ) {
                Text(
                    text = s.playNow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            // Separate clearly labeled CPU Practice CTA
            OutlinedButton(
                onClick = onCpuPractice,
                border = androidx.compose.foundation.BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ChampagneGold),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("cpu_practice_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = s.cpuPractice,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LobbyPillButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) ChampagneGold.copy(alpha = 0.16f) else VelvetSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (active) ChampagneGold.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        content()
    }
}
