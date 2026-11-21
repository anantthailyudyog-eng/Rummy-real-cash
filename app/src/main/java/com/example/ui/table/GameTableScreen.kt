package com.example.ui.table

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.domain.Card
import com.example.domain.ConnectionStatus
import com.example.domain.RummyEngine
import com.example.domain.SeatInfo
import com.example.domain.TableStatus
import com.example.domain.TurnPhase
import com.example.ui.VelvetUiState
import com.example.ui.components.Avatar3DView
import com.example.ui.components.CardBackView
import com.example.ui.components.PlayingCardView
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.EmeraldCta
import com.example.ui.theme.EmeraldCtaBright
import com.example.ui.theme.FeltCenter
import com.example.ui.theme.FeltEdge
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.MahoganyRim
import com.example.ui.theme.MahoganyRimDark
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SuitInk
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.VelvetSurface
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class GapKey(val groupIdx: Int, val posIdx: Int)

@Composable
fun GameTableScreen(
    uiState: VelvetUiState,
    onBackToLobby: () -> Unit,
    onRetryConnect: () -> Unit,
    onSwitchToCpu: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleLanguage: () -> Unit,
    onDrawClosed: () -> Unit,
    onDrawOpen: () -> Unit,
    onDiscard: (String?) -> Unit,
    onRequestDrop: () -> Unit,
    onConfirmDrop: () -> Unit,
    onDismissDrop: () -> Unit,
    onTapCard: (Card) -> Unit,
    onTapGap: (Int, Int) -> Unit,
    onDragCardToGap: (String, Int, Int) -> Unit,
    onSort: () -> Unit,
    onNewGroup: () -> Unit,
    onMoveHere: () -> Unit,
    onUngroup: () -> Unit,
    onOpenFinishReview: (String?) -> Unit,
    onCancelFinishReview: () -> Unit,
    onConfirmFinishReview: () -> Unit,
    onNextOrPlayAgain: () -> Unit,
    onShowInfoToast: (String) -> Unit
) {
    BackHandler { onBackToLobby() }

    val table = uiState.table ?: return
    val strings = uiState.strings
    val isMyTurn = table.status == TableStatus.PLAYING && table.activeTurnSeatIndex == table.mySeatIndex
    val mySeat = table.seats.getOrNull(table.mySeatIndex) ?: table.seats.firstOrNull()
    val opponents = table.seats.filter { it.playerId != uiState.profile.playerId }
    val cardsById = remember(table.myHand) { table.myHand.associateBy { it.id } }

    // Deal flight & Joker flip animation triggered ONLY when dealAnimTrigger changes!
    val dealAnim = remember { Animatable(1f) }
    LaunchedEffect(uiState.dealAnimTrigger) {
        if (uiState.dealAnimTrigger > 0) {
            dealAnim.snapTo(0f)
            dealAnim.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
        }
    }

    // Drag-and-drop root tracking
    val gapBounds = remember { mutableStateMapOf<GapKey, Rect>() }
    var finishBounds by remember { mutableStateOf<Rect?>(null) }
    var openPileBounds by remember { mutableStateOf<Rect?>(null) }
    var draggingCard by remember { mutableStateOf<Card?>(null) }
    var dragRootOffset by remember { mutableStateOf(Offset.Zero) }
    var hoveredGap by remember { mutableStateOf<GapKey?>(null) }
    var hoveringFinish by remember { mutableStateOf(false) }
    var hoveringOpenPile by remember { mutableStateOf(false) }

    fun updateDragTarget(pointerRoot: Offset) {
        hoveringFinish = finishBounds?.contains(pointerRoot) == true
        hoveringOpenPile = openPileBounds?.contains(pointerRoot) == true
        if (hoveringFinish || hoveringOpenPile) {
            hoveredGap = null
            return
        }
        var bestKey: GapKey? = null
        var bestDist = Float.MAX_VALUE
        gapBounds.forEach { (key, rect) ->
            val center = rect.center
            val dist = hypot(center.x - pointerRoot.x, center.y - pointerRoot.y)
            if (dist < 110f && dist < bestDist) {
                bestDist = dist
                bestKey = key
            }
        }
        hoveredGap = bestKey
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF10281E), ObsidianBg)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isCompactPortrait = maxWidth < 560.dp
        val cardW: Dp = if (isCompactPortrait) 42.dp else 46.dp
        val cardH: Dp = if (isCompactPortrait) 62.dp else 66.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Top HUD bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onBackToLobby,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("table_back_lobby")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.backLobby,
                            tint = ChampagneGold
                        )
                    }

                    ConnectionStatusChip(
                        isOfflineCpu = uiState.isOfflineCpu,
                        status = uiState.connectionStatus
                    )

                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "${table.mode.apiCode} · ${table.seats.size}/${table.maxSeats} · D${table.dealNumber.coerceAtLeast(1)}/${table.totalDeals}",
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 10.sp,
                            color = TextLight,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onToggleMute, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = strings.soundOn,
                            tint = if (uiState.isMuted) TextMuted else ChampagneGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleVoice, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = strings.voiceOn,
                            tint = if (uiState.isVoiceEnabled) ChampagneGold else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleLanguage)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.language.code.uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ChampagneGold
                        )
                    }
                }
            }

            // Middle Felt Table + Opponents + Center Piles
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompactPortrait) 8.dp else 18.dp, vertical = 4.dp)
            ) {
                // Mahogany leather padded rim & oval felt surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .shadow(16.dp, RoundedCornerShape(999.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.linearGradient(listOf(MahoganyRim, MahoganyRimDark)))
                        .border(2.dp, ChampagneGold.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.radialGradient(listOf(FeltCenter, FeltEdge)))
                            .border(1.5.dp, ChampagneGold.copy(alpha = 0.25f), RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_table_crest),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .alpha(0.14f)
                        )
                    }
                }

                // Opponents around upper edge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    opponents.forEach { opp ->
                        OpponentPod(
                            seat = opp,
                            isActive = table.status == TableStatus.PLAYING && table.activeTurnSeatIndex == opp.seatIndex
                        )
                    }
                }

                // Center Arena: Waiting countdown OR Joker/Stock/Open/Finish
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    if (table.status == TableStatus.WAITING) {
                        WaitingCenterBanner(
                            uiState = uiState,
                            seatedCount = table.seats.size,
                            maxSeats = table.maxSeats,
                            onRetry = onRetryConnect,
                            onSwitchToCpu = onSwitchToCpu
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (isCompactPortrait) 10.dp else 18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Wild Joker Indicator (flips face-up on deal, non-drawable)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(strings.jokerLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ChampagneGold)
                                    Spacer(Modifier.height(2.dp))
                                    val indicator = table.jokerIndicator
                                    if (indicator != null) {
                                        Box(
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    rotationY = (1f - dealAnim.value) * 90f
                                                }
                                                .clickable { onShowInfoToast(strings.jokerIndicatorInfo) }
                                        ) {
                                            PlayingCardView(
                                                card = indicator,
                                                wildRank = table.wildRank,
                                                width = cardW,
                                                height = cardH
                                            )
                                        }
                                    }
                                }

                                // 2. Closed Stock Pile
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(strings.stockLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextLight)
                                    Spacer(Modifier.height(2.dp))
                                    CardBackView(
                                        count = table.closedStockCount,
                                        width = cardW,
                                        height = cardH,
                                        modifier = Modifier
                                            .clickable(enabled = isMyTurn && table.turnPhase == TurnPhase.DRAW) {
                                                onDrawClosed()
                                            }
                                            .testTag("closed_stock_pile")
                                    )
                                }

                                // 3. Open Discard Pile (tappable to draw, drop-target to discard!)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${strings.openLabel} (${table.openPileCount})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextLight
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    val topOpen = table.openPileTop
                                    Box(
                                        modifier = Modifier
                                            .onGloballyPositioned { openPileBounds = it.boundsInRoot() }
                                            .border(
                                                width = if (hoveringOpenPile) 2.5.dp else 0.dp,
                                                color = if (hoveringOpenPile) ChampagneGold else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable(enabled = isMyTurn && table.turnPhase == TurnPhase.DRAW && topOpen != null) {
                                                onDrawOpen()
                                            }
                                            .testTag("open_discard_pile")
                                    ) {
                                        if (topOpen != null) {
                                            PlayingCardView(
                                                card = topOpen,
                                                wildRank = table.wildRank,
                                                width = cardW,
                                                height = cardH
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(cardW, cardH)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.5.dp, ChampagneGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("EMPTY", fontSize = 9.sp, color = TextMuted)
                                            }
                                        }
                                    }
                                }

                                // 4. Center Finish Target
                                val finishReady = isMyTurn && table.turnPhase == TurnPhase.DISCARD && (uiState.selectedCardIds.size == 1 || hoveringFinish)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(strings.finishLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF86EFAC))
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(width = cardW + 14.dp, height = cardH)
                                            .onGloballyPositioned { finishBounds = it.boundsInRoot() }
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (finishReady) EmeraldCta.copy(alpha = 0.35f) else EmeraldCta.copy(alpha = 0.16f)
                                            )
                                            .border(
                                                width = if (hoveringFinish || finishReady) 2.dp else 1.5.dp,
                                                color = if (hoveringFinish) ChampagneGold else EmeraldCtaBright,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { onOpenFinishReview(null) }
                                            .testTag("center_finish_target"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Filled.EmojiEvents,
                                                contentDescription = strings.finishLabel,
                                                tint = ChampagneGold,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = strings.finishLabel,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFBBF7D0)
                                            )
                                        }
                                    }
                                }
                            }

                            // Active turn guidance pill
                            Surface(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(999.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.45f))
                            ) {
                                val turnLabel = when {
                                    isMyTurn && table.turnPhase == TurnPhase.DRAW -> strings.yourTurnDraw
                                    isMyTurn && table.turnPhase == TurnPhase.DISCARD -> strings.yourTurnDiscard
                                    else -> "${strings.opponentTurn} ${table.seats.getOrNull(table.activeTurnSeatIndex)?.name.orEmpty()}"
                                }
                                Text(
                                    text = turnLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMyTurn) ChampagneGold else TextLight,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Player Dock: Seat status + Action Controls + 14-Card Hand
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.96f)))
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (mySeat != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(VelvetSurface)
                                .border(
                                    width = if (isMyTurn) 1.5.dp else 1.dp,
                                    color = if (isMyTurn) ChampagneGold else Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Avatar3DView(
                                avatarId = mySeat.avatarId,
                                name = mySeat.name,
                                size = 30.dp,
                                isActiveTurn = isMyTurn
                            )
                            Column {
                                Text(
                                    text = mySeat.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = TextLight,
                                    maxLines = 1
                                )
                                Text(
                                    text = "🪙${mySeat.chips} · ${mySeat.matchPoints}pt",
                                    fontFamily = JetBrainsMonoFont,
                                    fontSize = 10.sp,
                                    color = ChampagneGold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasSelection = uiState.selectedCardIds.isNotEmpty()
                        TableActionChip(
                            label = strings.sort,
                            enabled = table.myHand.isNotEmpty(),
                            onClick = onSort,
                            modifier = Modifier.testTag("sort_button")
                        )
                        TableActionChip(
                            label = strings.newGroup,
                            enabled = hasSelection,
                            onClick = onNewGroup,
                            modifier = Modifier.testTag("new_group_button")
                        )
                        TableActionChip(
                            label = strings.moveHere,
                            enabled = hasSelection,
                            onClick = onMoveHere,
                            modifier = Modifier.testTag("move_here_button")
                        )
                        TableActionChip(
                            label = strings.ungroup,
                            enabled = uiState.localGroups.size > 1,
                            onClick = onUngroup,
                            modifier = Modifier.testTag("ungroup_button")
                        )
                        TableActionChip(
                            label = strings.discard,
                            enabled = isMyTurn && table.turnPhase == TurnPhase.DISCARD && uiState.selectedCardIds.size == 1,
                            accentColor = ChampagneGold,
                            textColor = SuitInk,
                            onClick = { onDiscard(null) },
                            modifier = Modifier.testTag("discard_button")
                        )
                        val dropAllowed = mySeat != null &&
                            isMyTurn &&
                            table.turnPhase == TurnPhase.DRAW &&
                            RummyEngine.getDropPenalty(table.mode, mySeat.hasDrawnThisHand).allowed
                        TableActionChip(
                            label = strings.drop,
                            enabled = dropAllowed,
                            accentColor = DangerRed.copy(alpha = 0.85f),
                            textColor = Color.White,
                            onClick = onRequestDrop,
                            modifier = Modifier.testTag("drop_button")
                        )
                    }
                }

                // 14-Card Interactive Hand Tray (works even while opponent takes turn!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 4.dp, start = 4.dp, end = 12.dp)
                        .graphicsLayer {
                            translationY = (1f - dealAnim.value) * -90f
                            alpha = 0.35f + 0.65f * dealAnim.value
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val hasSelection = uiState.selectedCardIds.isNotEmpty()
                    uiState.localGroups.forEachIndexed { gIdx, groupIds ->
                        val groupCards = groupIds.mapNotNull { cardsById[it] }
                        val gType = RummyEngine.classifyGroup(groupCards, table.wildRank)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                groupCards.forEachIndexed { cIdx, card ->
                                    val gapKey = GapKey(gIdx, cIdx)
                                    InsertionGapSlot(
                                        showHint = hasSelection,
                                        isHovered = hoveredGap == gapKey,
                                        height = cardH,
                                        onPositioned = { gapBounds[gapKey] = it },
                                        onClick = { onTapGap(gIdx, cIdx) },
                                        testTag = "gap_${gIdx}_$cIdx"
                                    )

                                    var cardOriginRoot by remember(card.id) { mutableStateOf(Offset.Zero) }
                                    val isBeingDragged = draggingCard?.id == card.id

                                    Box(
                                        modifier = Modifier
                                            .onGloballyPositioned { cardOriginRoot = it.boundsInRoot().topLeft }
                                            .alpha(if (isBeingDragged) 0.25f else 1f)
                                            .pointerInput(card.id, uiState.localGroups) {
                                                detectDragGestures(
                                                    onDragStart = { startOffset ->
                                                        draggingCard = card
                                                        dragRootOffset = cardOriginRoot + startOffset
                                                        updateDragTarget(dragRootOffset)
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragRootOffset += dragAmount
                                                        updateDragTarget(dragRootOffset)
                                                    },
                                                    onDragEnd = {
                                                        val dragged = draggingCard
                                                        val targetGap = hoveredGap
                                                        val toFinish = hoveringFinish
                                                        val toOpen = hoveringOpenPile
                                                        draggingCard = null
                                                        hoveredGap = null
                                                        hoveringFinish = false
                                                        hoveringOpenPile = false
                                                        if (dragged != null) {
                                                            when {
                                                                toFinish -> onOpenFinishReview(dragged.id)
                                                                toOpen -> onDiscard(dragged.id)
                                                                targetGap != null -> onDragCardToGap(dragged.id, targetGap.groupIdx, targetGap.posIdx)
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        draggingCard = null
                                                        hoveredGap = null
                                                        hoveringFinish = false
                                                        hoveringOpenPile = false
                                                    }
                                                )
                                            }
                                            .clickable { onTapCard(card) }
                                            .testTag("hand_card_${card.id}")
                                    ) {
                                        PlayingCardView(
                                            card = card,
                                            wildRank = table.wildRank,
                                            isSelected = card.id in uiState.selectedCardIds,
                                            isNew = card.id == table.myLastDrawnCardId,
                                            width = cardW,
                                            height = cardH
                                        )
                                    }
                                }

                                val endKey = GapKey(gIdx, groupCards.size)
                                InsertionGapSlot(
                                    showHint = hasSelection,
                                    isHovered = hoveredGap == endKey,
                                    height = cardH,
                                    onPositioned = { gapBounds[endKey] = it },
                                    onClick = { onTapGap(gIdx, groupCards.size) },
                                    testTag = "gap_${gIdx}_${groupCards.size}"
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            GroupTypeChip(gType)
                        }
                    }

                    // Trailing slot to create a new group via drag or tap-gap
                    if (hasSelection || draggingCard != null) {
                        val newGroupKey = GapKey(uiState.localGroups.size, 0)
                        Box(
                            modifier = Modifier
                                .size(width = 34.dp, height = cardH)
                                .onGloballyPositioned { gapBounds[newGroupKey] = it.boundsInRoot() }
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (hoveredGap == newGroupKey) EmeraldCta.copy(alpha = 0.4f) else ChampagneGold.copy(alpha = 0.14f)
                                )
                                .border(1.5.dp, ChampagneGold, RoundedCornerShape(10.dp))
                                .clickable { onTapGap(uiState.localGroups.size, 0) }
                                .testTag("gap_new_group"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = ChampagneGold, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }

        // Floating dragged ghost card
        val activeDrag = draggingCard
        if (activeDrag != null) {
            Box(
                modifier = Modifier
                    .zIndex(50f)
                    .offset {
                        IntOffset(
                            (dragRootOffset.x - 60f).roundToInt(),
                            (dragRootOffset.y - 120f).roundToInt()
                        )
                    }
            ) {
                PlayingCardView(
                    card = activeDrag,
                    wildRank = table.wildRank,
                    isSelected = true,
                    width = cardW,
                    height = cardH
                )
            }
        }

        // Toast pill
        if (uiState.toastMessage != null) {
            Surface(
                color = VelvetSurface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ChampagneGold),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp)
                    .zIndex(60f)
            ) {
                Text(
                    text = uiState.toastMessage,
                    color = TextLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Modals
        uiState.finishReview?.let { review ->
            FinishReviewDialog(
                review = review,
                wildRank = table.wildRank,
                language = uiState.language,
                strings = strings,
                onCancel = onCancelFinishReview,
                onConfirm = onConfirmFinishReview
            )
        }

        uiState.dropConfirm?.let { penalty ->
            DropConfirmDialog(
                penalty = penalty,
                language = uiState.language,
                strings = strings,
                onCancel = onDismissDrop,
                onConfirm = onConfirmDrop
            )
        }

        val result = table.result
        if ((table.status == TableStatus.HAND_RESULT || table.status == TableStatus.MATCH_RESULT) && result != null) {
            WinnerResultDialog(
                result = result,
                localPlayerId = uiState.profile.playerId,
                wildRank = table.wildRank,
                strings = strings,
                onBackToLobby = onBackToLobby,
                onNextOrPlayAgain = onNextOrPlayAgain
            )
        }
    }
}

@Composable
private fun InsertionGapSlot(
    showHint: Boolean,
    isHovered: Boolean,
    height: Dp,
    onPositioned: (Rect) -> Unit,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .size(width = if (showHint || isHovered) 14.dp else 7.dp, height = height)
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .clickable(enabled = showHint, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        if (isHovered) {
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = height)
                    .clip(RoundedCornerShape(4.dp))
                    .background(EmeraldCtaBright)
            )
        } else if (showHint) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = height * 0.72f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ChampagneGold.copy(alpha = 0.55f))
            )
        }
    }
}

@Composable
private fun OpponentPod(seat: SeatInfo, isActive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ObsidianBg.copy(alpha = 0.88f))
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) ChampagneGold else Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(999.dp)
            )
            .alpha(if (seat.isDropped || seat.isEliminated) 0.52f else 1f)
            .padding(start = 4.dp, end = 9.dp, top = 3.dp, bottom = 3.dp)
    ) {
        Avatar3DView(
            avatarId = seat.avatarId,
            name = seat.name,
            size = 32.dp,
            isActiveTurn = isActive
        )
        Column {
            Text(
                text = seat.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${seat.cardCount}🃏 · ${seat.matchPoints}pt",
                fontFamily = JetBrainsMonoFont,
                fontSize = 9.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun WaitingCenterBanner(
    uiState: VelvetUiState,
    seatedCount: Int,
    maxSeats: Int,
    onRetry: () -> Unit,
    onSwitchToCpu: () -> Unit
) {
    val s = uiState.strings
    val isOffline = uiState.connectionStatus == ConnectionStatus.OFFLINE
    Surface(
        color = ObsidianBg.copy(alpha = 0.9f),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, ChampagneGold),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            if (isOffline) {
                Text(
                    text = s.serverOfflineHint,
                    fontSize = 12.sp,
                    color = TextLight,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.height(48.dp)) {
                        Text(s.retryConnect, color = ChampagneGold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onSwitchToCpu,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldCta),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("fallback_cpu_button")
                    ) {
                        Text(s.switchToCpu, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = if (uiState.countdownSeconds != null) s.searchingPlayers else s.waitingLone,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextLight,
                    textAlign = TextAlign.Center
                )
                if (uiState.countdownSeconds != null) {
                    Text(
                        text = "${s.dealingIn} ${uiState.countdownSeconds}s",
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ChampagneGold
                    )
                }
                Text(
                    text = "$seatedCount / $maxSeats seated",
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusChip(isOfflineCpu: Boolean, status: ConnectionStatus) {
    val (dotCol, label) = when {
        isOfflineCpu -> ChampagneGold to "CPU Offline"
        status == ConnectionStatus.CONNECTED -> EmeraldCtaBright to "Connected"
        status == ConnectionStatus.CONNECTING -> ChampagneGold to "Connecting..."
        status == ConnectionStatus.RECONNECTING -> ChampagneGold to "Reconnecting..."
        status == ConnectionStatus.OFFLINE -> DangerRed to "Offline"
        else -> TextMuted to "Idle"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotCol))
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextLight)
    }
}

@Composable
private fun TableActionChip(
    label: String,
    enabled: Boolean,
    accentColor: Color = Color.White.copy(alpha = 0.14f),
    textColor: Color = TextLight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) accentColor else Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (enabled) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) textColor else TextMuted.copy(alpha = 0.4f),
            maxLines = 1
        )
    }
}
