package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEffectsManager
import com.example.data.GuestProfile
import com.example.data.MultiplayerClient
import com.example.data.PreferencesRepository
import com.example.domain.Card
import com.example.domain.ConnectionStatus
import com.example.domain.CpuPracticeEngine
import com.example.domain.DropPenaltyInfo
import com.example.domain.FinishReviewState
import com.example.domain.GameMode
import com.example.domain.RummyEngine
import com.example.domain.SeatInfo
import com.example.domain.TableSnapshot
import com.example.domain.TableStatus
import com.example.domain.TurnPhase
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.i18n.StringsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

enum class AppScreen {
    LOBBY,
    TABLE
}

data class VelvetUiState(
    val screen: AppScreen = AppScreen.LOBBY,
    val profile: GuestProfile,
    val canClaimBonus: Boolean = true,
    val language: AppLanguage = AppLanguage.EN,
    val isMuted: Boolean = false,
    val isVoiceEnabled: Boolean = true,
    val selectedMode: GameMode = GameMode.POINTS,
    val selectedSeats: Int = 4,
    val isOfflineCpu: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val table: TableSnapshot? = null,
    val localGroups: List<List<String>> = emptyList(),
    val selectedCardIds: List<String> = emptyList(),
    val countdownSeconds: Int? = null,
    val dealAnimTrigger: Int = 0,
    val finishReview: FinishReviewState? = null,
    val dropConfirm: DropPenaltyInfo? = null,
    val toastMessage: String? = null,
    val isSubmittingFinish: Boolean = false
) {
    val strings: AppStrings get() = StringsRepository.forLanguage(language)
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesRepository(application)
    private val audio = SoundEffectsManager(application)
    private val client = MultiplayerClient()

    private var cpuEngine: CpuPracticeEngine? = null
    private var lastDealSequenceId = 0
    private var lastSeatedCount = 0
    private var countdownJob: Job? = null
    private var cpuTurnJob: Job? = null
    private var toastJob: Job? = null

    private val _uiState = MutableStateFlow(
        VelvetUiState(
            profile = prefs.getOrCreateGuestProfile(),
            canClaimBonus = prefs.canClaimDailyBonus(),
            language = prefs.getLanguage(),
            isMuted = prefs.isMuted(),
            isVoiceEnabled = prefs.isVoiceEnabled()
        ).also {
            audio.isMuted = it.isMuted
            audio.isVoiceEnabled = it.isVoiceEnabled
        }
    )
    val uiState: StateFlow<VelvetUiState> = _uiState.asStateFlow()

    fun markUserInteracted() {
        audio.markInteracted()
    }

    fun toggleMute() {
        markUserInteracted()
        val next = !_uiState.value.isMuted
        audio.isMuted = next
        if (next) audio.cancelSpeech()
        prefs.setMuted(next)
        _uiState.update { it.copy(isMuted = next) }
    }

    fun toggleVoice() {
        markUserInteracted()
        val next = !_uiState.value.isVoiceEnabled
        audio.isVoiceEnabled = next
        if (!next) audio.cancelSpeech()
        prefs.setVoiceEnabled(next)
        _uiState.update { it.copy(isVoiceEnabled = next) }
    }

    fun toggleLanguage() {
        markUserInteracted()
        val next = if (_uiState.value.language == AppLanguage.EN) AppLanguage.HI else AppLanguage.EN
        prefs.setLanguage(next)
        _uiState.update { it.copy(language = next) }
    }

    fun claimDailyBonus() {
        markUserInteracted()
        val updated = prefs.claimDailyBonus()
        audio.playWinCheer()
        _uiState.update {
            it.copy(profile = updated, canClaimBonus = prefs.canClaimDailyBonus())
        }
    }

    fun selectMode(mode: GameMode) {
        markUserInteracted()
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun selectSeats(seats: Int) {
        markUserInteracted()
        _uiState.update { it.copy(selectedSeats = seats.coerceIn(2, 6)) }
    }

    fun playNowMultiplayer() {
        markUserInteracted()
        val s = _uiState.value
        cpuTurnJob?.cancel()
        cpuEngine = null
        lastDealSequenceId = 0
        lastSeatedCount = 1

        // Seat player immediately while searching in background!
        val initialPreview = TableSnapshot(
            tableId = "pending",
            mode = s.selectedMode,
            maxSeats = s.selectedSeats,
            stake = 100,
            dealNumber = 0,
            totalDeals = s.selectedMode.totalDeals,
            dealSequenceId = 0,
            status = TableStatus.WAITING,
            countdownEndsAtMs = null,
            activeTurnSeatIndex = 0,
            turnPhase = TurnPhase.DRAW,
            drawnFromOpenCardId = null,
            jokerIndicator = null,
            wildRank = 1,
            openPileTop = null,
            openPileCount = 0,
            closedStockCount = 0,
            seats = listOf(
                SeatInfo(
                    playerId = s.profile.playerId,
                    name = s.profile.name,
                    avatarId = s.profile.avatarId,
                    seatIndex = 0,
                    chips = s.profile.chips,
                    matchPoints = 0,
                    lastHandPoints = 0,
                    cardCount = 0,
                    isDropped = false,
                    isEliminated = false,
                    wrongDeclaration = false,
                    hasDrawnThisHand = false,
                    connected = true
                )
            ),
            mySeatIndex = 0,
            myHand = emptyList(),
            myArrangedGroups = emptyList(),
            myLastDrawnCardId = null,
            result = null
        )

        audio.playSeat()
        _uiState.update {
            it.copy(
                screen = AppScreen.TABLE,
                isOfflineCpu = false,
                connectionStatus = ConnectionStatus.CONNECTING,
                table = initialPreview,
                localGroups = emptyList(),
                selectedCardIds = emptyList(),
                finishReview = null,
                dropConfirm = null
            )
        }
        connectToSharedBackend()
    }

    fun retryMultiplayerConnect() {
        if (_uiState.value.isOfflineCpu) return
        _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }
        connectToSharedBackend()
    }

    private fun connectToSharedBackend() {
        val s = _uiState.value
        viewModelScope.launch {
            val result = client.joinOrReconnect(
                profile = s.profile,
                mode = s.selectedMode,
                maxSeats = s.selectedSeats,
                stake = 100
            )
            result.onSuccess { joined ->
                if (joined.reconnectToken.isNotBlank()) {
                    prefs.saveReconnectToken(joined.reconnectToken)
                }
                _uiState.update {
                    it.copy(
                        profile = it.profile.copy(reconnectToken = joined.reconnectToken),
                        connectionStatus = ConnectionStatus.CONNECTED
                    )
                }
                applyIncomingTableSnapshot(joined.snapshot)
                client.connectWebSocket(
                    tableId = joined.snapshot.tableId,
                    playerId = s.profile.playerId,
                    onState = { snap -> applyIncomingTableSnapshot(snap) },
                    onStatus = { st -> _uiState.update { it.copy(connectionStatus = st) } },
                    onError = { err -> showToast(err) }
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.OFFLINE,
                        toastMessage = err.message
                    )
                }
            }
        }
    }

    fun startCpuPractice() {
        markUserInteracted()
        client.disconnectWebSocket()
        countdownJob?.cancel()
        cpuTurnJob?.cancel()
        val s = _uiState.value
        lastDealSequenceId = 0
        lastSeatedCount = s.selectedSeats

        val engine = CpuPracticeEngine(
            playerId = s.profile.playerId,
            playerName = s.profile.name,
            playerAvatarId = s.profile.avatarId,
            initialChips = s.profile.chips,
            mode = s.selectedMode,
            maxSeats = s.selectedSeats,
            stake = 100
        )
        cpuEngine = engine
        _uiState.update {
            it.copy(
                screen = AppScreen.TABLE,
                isOfflineCpu = true,
                connectionStatus = ConnectionStatus.OFFLINE,
                localGroups = emptyList(),
                selectedCardIds = emptyList(),
                finishReview = null,
                dropConfirm = null
            )
        }
        applyIncomingTableSnapshot(engine.snapshot())
    }

    private fun applyIncomingTableSnapshot(snap: TableSnapshot) {
        val current = _uiState.value
        val prevStatus = current.table?.status

        val isNewDeal = snap.dealSequenceId > lastDealSequenceId && snap.status == TableStatus.PLAYING
        if (isNewDeal) {
            lastDealSequenceId = snap.dealSequenceId
            audio.playDeal()
        }
        if (snap.seats.size > lastSeatedCount) {
            audio.playSeat()
        }
        lastSeatedCount = snap.seats.size

        // Preserve exact manual card arrangement!
        val updatedGroups = reconcileArrangement(
            currentLocal = if (isNewDeal) emptyList() else current.localGroups,
            serverHand = snap.myHand,
            serverArranged = snap.myArrangedGroups,
            tableId = snap.tableId,
            dealNumber = snap.dealNumber
        )

        var updatedProfile = current.profile
        if ((snap.status == TableStatus.HAND_RESULT || snap.status == TableStatus.MATCH_RESULT) && prevStatus == TableStatus.PLAYING) {
            val me = snap.seats.firstOrNull { it.playerId == current.profile.playerId }
            if (me != null) {
                prefs.saveChips(me.chips)
                updatedProfile = current.profile.copy(chips = me.chips)
            }
            val iWon = snap.result?.let {
                if (it.matchFinished) it.matchWinnerId == current.profile.playerId
                else it.handWinnerId == current.profile.playerId
            } ?: false
            if (iWon) audio.playWinCheer()
        }

        _uiState.update {
            it.copy(
                profile = updatedProfile,
                table = snap,
                localGroups = updatedGroups,
                selectedCardIds = if (isNewDeal) emptyList() else it.selectedCardIds.filter { id -> snap.myHand.any { c -> c.id == id } },
                dealAnimTrigger = if (isNewDeal) it.dealAnimTrigger + 1 else it.dealAnimTrigger,
                isSubmittingFinish = false
            )
        }

        if (snap.status == TableStatus.WAITING && snap.countdownEndsAtMs != null) {
            startCountdownTicker(snap.countdownEndsAtMs)
        } else {
            countdownJob?.cancel()
            _uiState.update { it.copy(countdownSeconds = null) }
        }

        if (current.isOfflineCpu && snap.status == TableStatus.PLAYING && snap.activeTurnSeatIndex != snap.mySeatIndex) {
            scheduleCpuTurn()
        }
    }

    private fun startCountdownTicker(endsAtMs: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val rem = endsAtMs - System.currentTimeMillis()
                if (rem <= 0) {
                    _uiState.update { it.copy(countdownSeconds = 0) }
                    break
                }
                val sec = ceil(rem / 1000.0).toInt()
                _uiState.update { it.copy(countdownSeconds = sec) }
                delay(250)
            }
        }
    }

    private fun scheduleCpuTurn() {
        cpuTurnJob?.cancel()
        cpuTurnJob = viewModelScope.launch {
            delay(650)
            val engine = cpuEngine ?: return@launch
            val snap = engine.stepCpuTurn()
            audio.playDiscard()
            applyIncomingTableSnapshot(snap)
        }
    }

    private fun reconcileArrangement(
        currentLocal: List<List<String>>,
        serverHand: List<Card>,
        serverArranged: List<List<String>>,
        tableId: String,
        dealNumber: Int
    ): List<List<String>> {
        if (serverHand.isEmpty()) return emptyList()
        val handIds = serverHand.map { it.id }.toSet()

        if (currentLocal.isNotEmpty() && currentLocal.flatten().toSet() == handIds && currentLocal.flatten().size == handIds.size) {
            return currentLocal
        }

        val persisted = prefs.loadArrangement(tableId, dealNumber)
        val base = when {
            currentLocal.isNotEmpty() -> currentLocal
            !persisted.isNullOrEmpty() -> persisted
            serverArranged.isNotEmpty() -> serverArranged
            else -> listOf(serverHand.map { it.id })
        }

        val cleaned = base.map { g -> g.filter { it in handIds } }.filter { it.isNotEmpty() }.toMutableList()
        val present = cleaned.flatten().toSet()
        val missing = serverHand.map { it.id }.filter { it !in present }
        if (missing.isNotEmpty()) {
            cleaned.add(missing)
        }
        prefs.saveArrangement(tableId, dealNumber, cleaned)
        return cleaned
    }

    private fun persistLocalGroups(groups: List<List<String>>) {
        val snap = _uiState.value.table ?: return
        prefs.saveArrangement(snap.tableId, snap.dealNumber, groups)
        cpuEngine?.updateArrangement(groups)
        if (!_uiState.value.isOfflineCpu && snap.tableId != "pending") {
            client.sendArrangementWs(snap.tableId, _uiState.value.profile.playerId, groups)
        }
    }

    fun toggleCardSelection(card: Card) {
        markUserInteracted()
        val currentSel = _uiState.value.selectedCardIds
        val nextSel = if (card.id in currentSel) {
            currentSel - card.id
        } else {
            audio.speakCard(card, _uiState.value.language)
            currentSel + card.id
        }
        _uiState.update { it.copy(selectedCardIds = nextSel) }
    }

    fun sortHand() {
        markUserInteracted()
        audio.cancelSpeech()
        val snap = _uiState.value.table ?: return
        if (snap.myHand.isEmpty()) return
        val sorted = RummyEngine.sortHandBySuitAndRank(snap.myHand).map { g -> g.map { it.id } }
        _uiState.update { it.copy(localGroups = sorted, selectedCardIds = emptyList()) }
        persistLocalGroups(sorted)
    }

    fun createNewGroupFromSelected() {
        markUserInteracted()
        val sel = _uiState.value.selectedCardIds
        if (sel.isEmpty()) return
        val selSet = sel.toSet()
        val moved = mutableListOf<String>()
        val remaining = _uiState.value.localGroups.map { g ->
            g.filter { id ->
                if (id in selSet) {
                    moved.add(id)
                    false
                } else {
                    true
                }
            }
        }.filter { it.isNotEmpty() }.toMutableList()
        remaining.add(moved)
        _uiState.update { it.copy(localGroups = remaining, selectedCardIds = emptyList()) }
        persistLocalGroups(remaining)
    }

    fun ungroupAll() {
        markUserInteracted()
        val flat = _uiState.value.localGroups.flatten()
        val next = if (flat.isEmpty()) emptyList() else listOf(flat)
        _uiState.update { it.copy(localGroups = next, selectedCardIds = emptyList()) }
        persistLocalGroups(next)
    }

    fun moveSelectedTo(targetGroupIdx: Int, targetPosInGroup: Int) {
        markUserInteracted()
        val sel = _uiState.value.selectedCardIds
        if (sel.isEmpty()) return
        moveCardIdsTo(sel, targetGroupIdx, targetPosInGroup)
    }

    fun moveSingleCardTo(cardId: String, targetGroupIdx: Int, targetPosInGroup: Int) {
        markUserInteracted()
        moveCardIdsTo(listOf(cardId), targetGroupIdx, targetPosInGroup)
    }

    private fun moveCardIdsTo(cardIdsToMove: List<String>, targetGroupIdx: Int, targetPosInGroup: Int) {
        val current = _uiState.value.localGroups
        val moveSet = cardIdsToMove.toSet()
        val orderedMoving = mutableListOf<String>()
        val nextGroups = mutableListOf<MutableList<String>>()

        current.forEachIndexed { gIdx, group ->
            val kept = mutableListOf<String>()
            var adjustedPos = targetPosInGroup
            group.forEachIndexed { cIdx, id ->
                if (id in moveSet) {
                    orderedMoving.add(id)
                    if (gIdx == targetGroupIdx && cIdx < targetPosInGroup) {
                        adjustedPos--
                    }
                } else {
                    kept.add(id)
                }
            }
            if (gIdx == targetGroupIdx) {
                val clamped = adjustedPos.coerceIn(0, kept.size)
                kept.addAll(clamped, orderedMoving)
            }
            nextGroups.add(kept)
        }

        if (targetGroupIdx >= current.size) {
            nextGroups.add(orderedMoving)
        }

        val cleaned = nextGroups.map { it.toList() }.filter { it.isNotEmpty() }
        _uiState.update { it.copy(localGroups = cleaned, selectedCardIds = emptyList()) }
        persistLocalGroups(cleaned)
    }

    fun drawClosed() {
        markUserInteracted()
        val s = _uiState.value
        val snap = s.table ?: return
        if (snap.status != TableStatus.PLAYING || snap.activeTurnSeatIndex != snap.mySeatIndex || snap.turnPhase != TurnPhase.DRAW) return

        if (s.isOfflineCpu) {
            val next = cpuEngine?.drawClosed() ?: return
            audio.playDraw()
            applyIncomingTableSnapshot(next)
        } else {
            viewModelScope.launch {
                client.sendAction(snap.tableId, s.profile.playerId, JSONObject().put("type", "DRAW_CLOSED"))
                    .onSuccess {
                        audio.playDraw()
                        applyIncomingTableSnapshot(it)
                    }
                    .onFailure { showToast(it.message ?: "Draw failed") }
            }
        }
    }

    fun drawOpen() {
        markUserInteracted()
        val s = _uiState.value
        val snap = s.table ?: return
        if (snap.status != TableStatus.PLAYING || snap.activeTurnSeatIndex != snap.mySeatIndex || snap.turnPhase != TurnPhase.DRAW) return

        if (s.isOfflineCpu) {
            val next = cpuEngine?.drawOpen() ?: return
            audio.playDraw()
            applyIncomingTableSnapshot(next)
        } else {
            viewModelScope.launch {
                client.sendAction(snap.tableId, s.profile.playerId, JSONObject().put("type", "DRAW_OPEN"))
                    .onSuccess {
                        audio.playDraw()
                        applyIncomingTableSnapshot(it)
                    }
                    .onFailure { showToast(it.message ?: "Draw failed") }
            }
        }
    }

    fun discardSelectedOrCard(explicitCardId: String? = null) {
        markUserInteracted()
        val s = _uiState.value
        val snap = s.table ?: return
        val cardId = explicitCardId ?: s.selectedCardIds.singleOrNull() ?: return
        if (snap.status != TableStatus.PLAYING || snap.activeTurnSeatIndex != snap.mySeatIndex || snap.turnPhase != TurnPhase.DISCARD) return

        if (cardId == snap.drawnFromOpenCardId) {
            audio.playError()
            showToast(s.strings.cannotDiscardOpenCard)
            return
        }

        if (s.isOfflineCpu) {
            val res = cpuEngine?.discard(cardId) ?: return
            res.onSuccess {
                audio.playDiscard()
                _uiState.update { st -> st.copy(selectedCardIds = emptyList()) }
                applyIncomingTableSnapshot(it)
            }.onFailure {
                audio.playError()
                showToast(it.message ?: s.strings.cannotDiscardOpenCard)
            }
        } else {
            viewModelScope.launch {
                client.sendAction(
                    snap.tableId,
                    s.profile.playerId,
                    JSONObject().put("type", "DISCARD").put("cardId", cardId)
                ).onSuccess {
                    audio.playDiscard()
                    _uiState.update { st -> st.copy(selectedCardIds = emptyList()) }
                    applyIncomingTableSnapshot(it)
                }.onFailure {
                    audio.playError()
                    showToast(it.message ?: "Discard failed")
                }
            }
        }
    }

    fun requestDrop() {
        markUserInteracted()
        val s = _uiState.value
        val snap = s.table ?: return
        val mySeat = snap.seats.getOrNull(snap.mySeatIndex) ?: return
        val penalty = RummyEngine.getDropPenalty(snap.mode, mySeat.hasDrawnThisHand)
        if (!penalty.allowed) {
            showToast(if (s.language == AppLanguage.HI) penalty.reasonHi else penalty.reasonEn)
            return
        }
        _uiState.update { it.copy(dropConfirm = penalty) }
    }

    fun dismissDropConfirm() {
        _uiState.update { it.copy(dropConfirm = null) }
    }

    fun confirmDrop() {
        markUserInteracted()
        val s = _uiState.value
        val snap = s.table ?: return
        _uiState.update { it.copy(dropConfirm = null) }

        if (s.isOfflineCpu) {
            val next = cpuEngine?.dropPlayer() ?: return
            audio.playDiscard()
            applyIncomingTableSnapshot(next)
        } else {
            viewModelScope.launch {
                client.sendAction(snap.tableId, s.profile.playerId, JSONObject().put("type", "DROP"))
                    .onSuccess {
                        audio.playDiscard()
                        applyIncomingTableSnapshot(it)
                    }
                    .onFailure { showToast(it.message ?: "Drop failed") }
            }
        }
    }

    fun openFinishReview(explicitCardId: String? = null) {
        markUserInteracted()
        val s = _uiState.value
        val snap = s.table ?: return
        if (snap.status != TableStatus.PLAYING || snap.activeTurnSeatIndex != snap.mySeatIndex || snap.turnPhase != TurnPhase.DISCARD) {
            showToast(s.strings.yourTurnDraw)
            return
        }
        val finishId = explicitCardId ?: s.selectedCardIds.singleOrNull()
        if (finishId == null) {
            showToast("Select 1 card to finish")
            return
        }
        val byId = snap.myHand.associateBy { it.id }
        val finishCard = byId[finishId] ?: return
        val remainingGroups = s.localGroups
            .map { g -> g.filter { it != finishId } }
            .filter { it.isNotEmpty() }
        val groupCards = remainingGroups.map { g -> g.mapNotNull { byId[it] } }
        val validation = RummyEngine.validateDeclaration(groupCards, snap.wildRank)

        _uiState.update {
            it.copy(
                finishReview = FinishReviewState(
                    finishCard = finishCard,
                    remainingGroups = remainingGroups,
                    validation = validation
                )
            )
        }
    }

    fun cancelFinishReview() {
        // Put selected card back in its exact position (localGroups was never mutated!)
        _uiState.update { it.copy(finishReview = null, selectedCardIds = emptyList()) }
    }

    fun confirmFinishDeclaration() {
        markUserInteracted()
        val s = _uiState.value
        val fr = s.finishReview ?: return
        val snap = s.table ?: return
        if (s.isSubmittingFinish) return

        _uiState.update { it.copy(finishReview = null, selectedCardIds = emptyList(), isSubmittingFinish = true) }

        if (s.isOfflineCpu) {
            val next = cpuEngine?.submitFinish(fr.finishCard.id, fr.remainingGroups) ?: return
            applyIncomingTableSnapshot(next)
        } else {
            val arr = JSONArray()
            fr.remainingGroups.forEach { g ->
                val inner = JSONArray()
                g.forEach { inner.put(it) }
                arr.put(inner)
            }
            viewModelScope.launch {
                client.sendAction(
                    snap.tableId,
                    s.profile.playerId,
                    JSONObject()
                        .put("type", "SUBMIT_FINISH")
                        .put("finishCardId", fr.finishCard.id)
                        .put("groups", arr)
                ).onSuccess {
                    applyIncomingTableSnapshot(it)
                }.onFailure { err ->
                    _uiState.update { it.copy(isSubmittingFinish = false) }
                    showToast(err.message ?: "Finish failed")
                }
            }
        }
    }

    fun nextDealOrPlayAgain() {
        markUserInteracted()
        val s = _uiState.value
        val snap = s.table ?: return
        val type = if (snap.result?.matchFinished == true) "PLAY_AGAIN" else "NEXT_DEAL"

        if (s.isOfflineCpu) {
            val next = cpuEngine?.startNextDeal() ?: return
            applyIncomingTableSnapshot(next)
        } else {
            viewModelScope.launch {
                client.sendAction(snap.tableId, s.profile.playerId, JSONObject().put("type", type))
                    .onSuccess { applyIncomingTableSnapshot(it) }
                    .onFailure { showToast(it.message ?: "Failed") }
            }
        }
    }

    fun backToLobby() {
        markUserInteracted()
        audio.cancelSpeech()
        countdownJob?.cancel()
        cpuTurnJob?.cancel()
        client.disconnectWebSocket()
        val pid = _uiState.value.profile.playerId
        if (!_uiState.value.isOfflineCpu) {
            viewModelScope.launch { client.leaveTable(pid) }
        }
        cpuEngine = null
        _uiState.update {
            it.copy(
                screen = AppScreen.LOBBY,
                table = null,
                localGroups = emptyList(),
                selectedCardIds = emptyList(),
                finishReview = null,
                dropConfirm = null,
                connectionStatus = ConnectionStatus.IDLE,
                countdownSeconds = null
            )
        }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.disconnectWebSocket()
        audio.release()
    }
}
