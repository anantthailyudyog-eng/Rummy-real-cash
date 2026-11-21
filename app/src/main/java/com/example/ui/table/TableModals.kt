package com.example.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.DropPenaltyInfo
import com.example.domain.FinishReviewState
import com.example.domain.GroupType
import com.example.domain.HandResultSummary
import com.example.domain.RummyEngine
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.ui.components.PlayingCardView
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.EmeraldCta
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.SkyHighlight
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.VelvetSurface
import com.example.ui.theme.VelvetSurfaceElevated

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinishReviewDialog(
    review: FinishReviewState,
    wildRank: Int,
    language: AppLanguage,
    strings: AppStrings,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = VelvetSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ChampagneGold),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.finishReviewTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = ChampagneGold
                )
                Text(text = strings.finishReviewNote, fontSize = 13.sp, color = TextMuted)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Discard", fontSize = 10.sp, color = TextMuted)
                        PlayingCardView(
                            card = review.finishCard,
                            wildRank = wildRank,
                            width = 44.dp,
                            height = 64.dp
                        )
                    }
                    val ok = review.validation.isValid
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (ok) EmeraldCta.copy(alpha = 0.18f) else DangerRed.copy(alpha = 0.2f))
                            .border(1.dp, if (ok) EmeraldCta else DangerRed, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = if (ok) "VALID DECLARATION" else "INVALID DECLARATION (80 pt penalty)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = if (ok) Color(0xFF86EFAC) else Color(0xFFFFCDD2)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = if (language == AppLanguage.HI) review.validation.reasonHi else review.validation.reasonEn,
                                fontSize = 12.sp,
                                color = TextLight
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    review.validation.groupResults.forEach { groupItem ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(VelvetSurfaceElevated)
                                .padding(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                groupItem.cards.forEach { c ->
                                    PlayingCardView(card = c, wildRank = wildRank, width = 38.dp, height = 56.dp)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            GroupTypeChip(groupItem.type)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("finish_cancel_button")
                    ) {
                        Text(strings.cancel, color = TextLight)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldCta),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("finish_confirm_button")
                    ) {
                        Text(strings.confirmDeclare, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DropConfirmDialog(
    penalty: DropPenaltyInfo,
    language: AppLanguage,
    strings: AppStrings,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
            color = VelvetSurface,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ChampagneGold)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.dropConfirmTitle, style = MaterialTheme.typography.headlineMedium, color = ChampagneGold)
                Text(
                    text = if (language == AppLanguage.HI) penalty.reasonHi else penalty.reasonEn,
                    fontSize = 14.sp,
                    color = TextLight
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.height(48.dp)) {
                        Text(strings.cancel, color = TextLight)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("confirm_drop_button")
                    ) {
                        Text(strings.dropConfirmAction, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WinnerResultDialog(
    result: HandResultSummary,
    localPlayerId: String,
    wildRank: Int,
    strings: AppStrings,
    onBackToLobby: () -> Unit,
    onNextOrPlayAgain: () -> Unit
) {
    val iWon = if (result.matchFinished) result.matchWinnerId == localPlayerId else result.handWinnerId == localPlayerId
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = VelvetSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ChampagneGold),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (iWon) strings.congrats else strings.handOver,
                    style = MaterialTheme.typography.headlineLarge,
                    color = ChampagneGold
                )
                Text(
                    text = "🏆 ${if (result.matchFinished) result.matchWinnerName else result.handWinnerName} · Deal ${result.dealNumber}/${result.totalDeals}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextLight
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    result.winningGroups.forEach { group ->
                        val gType = RummyEngine.classifyGroup(group, wildRank)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(VelvetSurfaceElevated)
                                .padding(6.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                group.forEach { c ->
                                    PlayingCardView(card = c, wildRank = wildRank, width = 36.dp, height = 52.dp)
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            GroupTypeChip(gType)
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.handScores.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${row.name} ${if (row.isWinner) "👑" else ""}",
                                fontWeight = if (row.isWinner) FontWeight.Bold else FontWeight.Normal,
                                color = if (row.isWinner) ChampagneGold else TextLight,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${row.handPoints} pt (Total ${row.matchPoints})",
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                if (result.matchFinished) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChampagneGold.copy(alpha = 0.12f))
                            .border(1.dp, ChampagneGold, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(strings.grossWin, fontSize = 11.sp, color = TextMuted)
                            Text("🪙 ${result.grossReward}", fontFamily = JetBrainsMonoFont, fontWeight = FontWeight.Bold, color = TextLight)
                        }
                        Column {
                            Text(strings.commissionLabel, fontSize = 11.sp, color = TextMuted)
                            Text("-🪙 ${result.commission}", fontFamily = JetBrainsMonoFont, fontWeight = FontWeight.Bold, color = DangerRed)
                        }
                        Column {
                            Text(strings.netReward, fontSize = 11.sp, color = TextMuted)
                            Text("+🪙 ${result.netReward}", fontFamily = JetBrainsMonoFont, fontWeight = FontWeight.ExtraBold, color = ChampagneGold)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = onBackToLobby,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("result_lobby_button")
                    ) {
                        Text(strings.backLobby, color = TextLight)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onNextOrPlayAgain,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldCta),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("result_play_again_button")
                    ) {
                        Text(if (result.matchFinished) strings.playAgain else strings.nextDeal, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun GroupTypeChip(type: GroupType) {
    val (bg, fg, label) = when (type) {
        GroupType.PURE_SEQUENCE -> Triple(EmeraldCta.copy(alpha = 0.3f), Color(0xFF86EFAC), "PURE SEQ")
        GroupType.IMPURE_SEQUENCE -> Triple(SkyHighlight.copy(alpha = 0.28f), Color(0xFFBAE6FD), "IMPURE SEQ")
        GroupType.SET -> Triple(ChampagneGold.copy(alpha = 0.28f), Color(0xFFFDE68A), "SET")
        GroupType.INVALID -> Triple(Color.White.copy(alpha = 0.1f), TextMuted, "PLAN")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = label, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
