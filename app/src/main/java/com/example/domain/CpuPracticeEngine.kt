package com.example.domain

import kotlin.math.max
import kotlin.math.min

class CpuPracticeEngine(
    private val playerId: String,
    private val playerName: String,
    private val playerAvatarId: Int,
    initialChips: Int,
    private val mode: GameMode,
    private val maxSeats: Int,
    private val stake: Int = 100
) {
    private data class MutableSeat(
        val playerId: String,
        val name: String,
        val avatarId: Int,
        val seatIndex: Int,
        var chips: Int,
        var matchPoints: Int = 0,
        var lastHandPoints: Int = 0,
        var isDropped: Boolean = false,
        var isEliminated: Boolean = false,
        var wrongDeclaration: Boolean = false,
        var hasDrawnThisHand: Boolean = false,
        var lastDrawnCardId: String? = null,
        val hand: MutableList<Card> = mutableListOf()
    )

    private val cpuNames = listOf("Aarav", "Meera", "Kabir", "Diya", "Rohan")
    private val seats = mutableListOf<MutableSeat>()
    private var dealNumber = 0
    private var dealSequenceId = 0
    private var status = TableStatus.PLAYING
    private var activeTurnSeatIndex = 0
    private var turnPhase = TurnPhase.DRAW
    private var drawnFromOpenCardId: String? = null
    private var jokerIndicator: Card? = null
    private var wildRank = 1
    private var openPile = mutableListOf<Card>()
    private var closedStock = mutableListOf<Card>()
    private var lastResult: HandResultSummary? = null
    private var matchEntryDeducted = false
    private var settlementApplied = false
    private var playerArrangement = listOf<List<String>>()

    init {
        seats.add(
            MutableSeat(
                playerId = playerId,
                name = playerName,
                avatarId = playerAvatarId,
                seatIndex = 0,
                chips = initialChips
            )
        )
        for (i in 1 until maxSeats) {
            seats.add(
                MutableSeat(
                    playerId = "cpu_$i",
                    name = "${cpuNames[(i - 1) % cpuNames.size]} (CPU)",
                    avatarId = i,
                    seatIndex = i,
                    chips = 5000
                )
            )
        }
        startNextDeal()
    }

    fun startNextDeal(): TableSnapshot {
        if (status == TableStatus.MATCH_RESULT) {
            dealNumber = 0
            matchEntryDeducted = false
            seats.forEach {
                it.matchPoints = 0
                it.isEliminated = false
                it.isDropped = false
            }
        }
        if (!matchEntryDeducted && mode != GameMode.POINTS) {
            seats.forEach { it.chips = max(0, it.chips - stake) }
            matchEntryDeducted = true
        }
        val activeSeats = seats.filter { !it.isEliminated }
        dealNumber += 1
        dealSequenceId += 1
        status = TableStatus.PLAYING
        settlementApplied = false
        lastResult = null
        drawnFromOpenCardId = null
        turnPhase = TurnPhase.DRAW

        val deck = RummyEngine.secureShuffle(RummyEngine.createDeck106())
        val dealt = RummyEngine.dealHandFromDeck(deck, activeSeats.size)
        jokerIndicator = dealt.jokerIndicator
        wildRank = dealt.wildRank
        openPile = dealt.openPile
        closedStock = dealt.closedStock

        activeSeats.forEachIndexed { idx, seat ->
            seat.hand.clear()
            seat.hand.addAll(dealt.hands[idx])
            seat.isDropped = false
            seat.hasDrawnThisHand = false
            seat.lastDrawnCardId = null
            seat.wrongDeclaration = false
            seat.lastHandPoints = 0
        }
        playerArrangement = listOf(seats[0].hand.map { it.id })
        activeTurnSeatIndex = seats.indexOfFirst { !it.isEliminated }.coerceAtLeast(0)
        return snapshot()
    }

    fun updateArrangement(groups: List<List<String>>) {
        playerArrangement = groups
    }

    fun drawClosed(): TableSnapshot {
        require(status == TableStatus.PLAYING && activeTurnSeatIndex == 0 && turnPhase == TurnPhase.DRAW)
        val card = popClosed() ?: error("Stock empty")
        val me = seats[0]
        me.hand.add(card)
        me.lastDrawnCardId = card.id
        me.hasDrawnThisHand = true
        turnPhase = TurnPhase.DISCARD
        drawnFromOpenCardId = null
        playerArrangement = playerArrangement + listOf(listOf(card.id))
        return snapshot()
    }

    fun drawOpen(): TableSnapshot {
        require(status == TableStatus.PLAYING && activeTurnSeatIndex == 0 && turnPhase == TurnPhase.DRAW)
        if (openPile.isEmpty()) error("Open pile empty")
        val card = openPile.removeAt(openPile.lastIndex)
        val me = seats[0]
        me.hand.add(card)
        me.lastDrawnCardId = card.id
        me.hasDrawnThisHand = true
        turnPhase = TurnPhase.DISCARD
        drawnFromOpenCardId = card.id
        playerArrangement = playerArrangement + listOf(listOf(card.id))
        return snapshot()
    }

    fun discard(cardId: String): Result<TableSnapshot> {
        if (status != TableStatus.PLAYING || activeTurnSeatIndex != 0 || turnPhase != TurnPhase.DISCARD) {
            return Result.failure(IllegalStateException("Draw a card first"))
        }
        if (cardId == drawnFromOpenCardId) {
            return Result.failure(IllegalArgumentException("Cannot immediately discard the exact card just drawn from the open pile"))
        }
        val me = seats[0]
        val idx = me.hand.indexOfFirst { it.id == cardId }
        if (idx == -1) return Result.failure(IllegalArgumentException("Card not in hand"))
        val removed = me.hand.removeAt(idx)
        openPile.add(removed)
        if (me.lastDrawnCardId == cardId) me.lastDrawnCardId = null
        playerArrangement = playerArrangement.map { g -> g.filter { it != cardId } }.filter { it.isNotEmpty() }
        advanceTurn()
        return Result.success(snapshot())
    }

    fun dropPlayer(): TableSnapshot {
        require(status == TableStatus.PLAYING && activeTurnSeatIndex == 0 && turnPhase == TurnPhase.DRAW)
        val me = seats[0]
        val penalty = RummyEngine.getDropPenalty(mode, me.hasDrawnThisHand)
        require(penalty.allowed) { penalty.reasonEn }
        me.isDropped = true
        me.lastHandPoints = penalty.points
        val remaining = seats.filter { !it.isDropped && !it.isEliminated }
        if (remaining.size == 1) {
            settleHand(remaining[0], listOf(remaining[0].hand.toList()))
        } else {
            advanceTurn()
        }
        return snapshot()
    }

    fun submitFinish(finishCardId: String, groupsIds: List<List<String>>): TableSnapshot {
        require(status == TableStatus.PLAYING && activeTurnSeatIndex == 0 && turnPhase == TurnPhase.DISCARD)
        val me = seats[0]
        val finishIdx = me.hand.indexOfFirst { it.id == finishCardId }
        require(finishIdx != -1) { "Finish card not in hand" }
        val finishCard = me.hand.removeAt(finishIdx)
        openPile.add(finishCard)
        val byId = me.hand.associateBy { it.id }
        val groupCards = groupsIds.map { g -> g.mapNotNull { byId[it] } }
        val validation = RummyEngine.validateDeclaration(groupCards, wildRank, me.hand)

        if (validation.isValid) {
            settleHand(me, groupCards)
        } else {
            me.wrongDeclaration = true
            me.isDropped = true
            me.lastHandPoints = 80
            val remaining = seats.filter { !it.isDropped && !it.isEliminated }
            if (remaining.size == 1) {
                settleHand(remaining[0], listOf(remaining[0].hand.toList()))
            } else {
                advanceTurn()
            }
        }
        return snapshot()
    }

    fun stepCpuTurn(): TableSnapshot {
        if (status != TableStatus.PLAYING || activeTurnSeatIndex == 0) return snapshot()
        val cpu = seats[activeTurnSeatIndex]
        if (cpu.isDropped || cpu.isEliminated) {
            advanceTurn()
            return snapshot()
        }
        val topOpen = openPile.lastOrNull()
        val takeOpen = topOpen != null && RummyEngine.isWildCard(topOpen, wildRank)
        val drawn = if (takeOpen) openPile.removeAt(openPile.lastIndex) else popClosed()
        if (drawn != null) {
            cpu.hand.add(drawn)
            cpu.hasDrawnThisHand = true
        }

        // Check if CPU naturally assembled a winning 14-card hand (discarding 1 card gives 0 deadwood)
        if (cpu.hand.size == 14) {
            for (candidateIdx in cpu.hand.indices) {
                val cand = cpu.hand[candidateIdx]
                if (takeOpen && cand.id == drawn?.id) continue
                val rest = cpu.hand.filterIndexed { idx, _ -> idx != candidateIdx }
                val sorted = RummyEngine.sortHandBySuitAndRank(rest)
                if (RummyEngine.validateDeclaration(sorted, wildRank, rest).isValid) {
                    cpu.hand.removeAt(candidateIdx)
                    openPile.add(cand)
                    settleHand(cpu, sorted)
                    return snapshot()
                }
            }
        }

        // Otherwise discard highest-point non-wild card (not the drawn open card)
        val discardCard = cpu.hand
            .filter { !takeOpen || it.id != drawn?.id }
            .maxByOrNull { RummyEngine.cardPointValue(it, wildRank) }
            ?: cpu.hand.first()
        cpu.hand.remove(discardCard)
        openPile.add(discardCard)
        advanceTurn()
        return snapshot()
    }

    private fun popClosed(): Card? {
        if (closedStock.isEmpty() && openPile.size > 1) {
            val top = openPile.removeAt(openPile.lastIndex)
            closedStock = RummyEngine.secureShuffle(openPile)
            openPile = mutableListOf(top)
        }
        return if (closedStock.isNotEmpty()) closedStock.removeAt(closedStock.lastIndex) else null
    }

    private fun advanceTurn() {
        for (step in 1..seats.size) {
            val idx = (activeTurnSeatIndex + step) % seats.size
            if (!seats[idx].isDropped && !seats[idx].isEliminated) {
                activeTurnSeatIndex = idx
                turnPhase = TurnPhase.DRAW
                drawnFromOpenCardId = null
                return
            }
        }
    }

    private fun settleHand(winnerSeat: MutableSeat, winningGroups: List<List<Card>>) {
        if (settlementApplied) return
        settlementApplied = true

        val rows = seats.filter { !it.isEliminated }.map { seat ->
            val pts = when {
                seat.playerId == winnerSeat.playerId -> 0
                seat.wrongDeclaration -> 80
                seat.isDropped -> seat.lastHandPoints
                else -> RummyEngine.calculateDeadwoodPoints(seat.hand, wildRank)
            }
            seat.lastHandPoints = pts
            seat.matchPoints += pts
            PlayerScoreRow(
                playerId = seat.playerId,
                name = seat.name,
                avatarId = seat.avatarId,
                handPoints = pts,
                matchPoints = seat.matchPoints,
                isWinner = seat.playerId == winnerSeat.playerId,
                isDropped = seat.isDropped,
                wrongDeclaration = seat.wrongDeclaration
            )
        }

        var matchFinished = false
        var matchWinner = winnerSeat
        var grossReward = 0
        var commission = 0
        var netReward = 0

        when (mode) {
            GameMode.POINTS -> {
                matchFinished = true
                val pointChipValue = max(1, stake / 80)
                var totalLost = 0
                for (s in seats) {
                    if (s.playerId == winnerSeat.playerId) continue
                    val loss = min(s.chips, s.lastHandPoints * pointChipValue)
                    s.chips -= loss
                    totalLost += loss
                }
                val settled = RummyEngine.computeCommissionAndNet(totalLost)
                grossReward = settled.grossReward
                commission = settled.commission
                netReward = settled.netReward
                winnerSeat.chips += netReward
            }
            GameMode.POOL_101, GameMode.POOL_201 -> {
                val limit = if (mode == GameMode.POOL_101) 101 else 201
                seats.forEach { if (it.matchPoints >= limit) it.isEliminated = true }
                val survivors = seats.filter { !it.isEliminated }
                if (survivors.size <= 1) {
                    matchFinished = true
                    matchWinner = survivors.firstOrNull() ?: seats.minBy { it.matchPoints }
                    val settled = RummyEngine.computeCommissionAndNet(stake * seats.size)
                    grossReward = settled.grossReward
                    commission = settled.commission
                    netReward = settled.netReward
                    matchWinner.chips += netReward
                }
            }
            GameMode.DEALS_3, GameMode.DEALS_6 -> {
                if (dealNumber >= mode.totalDeals) {
                    matchFinished = true
                    matchWinner = seats.minBy { it.matchPoints }
                    val settled = RummyEngine.computeCommissionAndNet(stake * seats.size)
                    grossReward = settled.grossReward
                    commission = settled.commission
                    netReward = settled.netReward
                    matchWinner.chips += netReward
                }
            }
        }

        status = if (matchFinished) TableStatus.MATCH_RESULT else TableStatus.HAND_RESULT
        lastResult = HandResultSummary(
            handWinnerId = winnerSeat.playerId,
            handWinnerName = winnerSeat.name,
            matchWinnerId = if (matchFinished) matchWinner.playerId else null,
            matchWinnerName = if (matchFinished) matchWinner.name else null,
            matchFinished = matchFinished,
            dealNumber = dealNumber,
            totalDeals = mode.totalDeals,
            winningGroups = winningGroups,
            handScores = rows,
            grossReward = grossReward,
            commission = commission,
            netReward = netReward
        )
    }

    fun snapshot(): TableSnapshot {
        val me = seats[0]
        return TableSnapshot(
            tableId = "cpu_local",
            mode = mode,
            maxSeats = maxSeats,
            stake = stake,
            dealNumber = dealNumber,
            totalDeals = mode.totalDeals,
            dealSequenceId = dealSequenceId,
            status = status,
            countdownEndsAtMs = null,
            activeTurnSeatIndex = activeTurnSeatIndex,
            turnPhase = turnPhase,
            drawnFromOpenCardId = if (activeTurnSeatIndex == 0) drawnFromOpenCardId else null,
            jokerIndicator = jokerIndicator,
            wildRank = wildRank,
            openPileTop = openPile.lastOrNull(),
            openPileCount = openPile.size,
            closedStockCount = closedStock.size,
            seats = seats.map {
                SeatInfo(
                    playerId = it.playerId,
                    name = it.name,
                    avatarId = it.avatarId,
                    seatIndex = it.seatIndex,
                    chips = it.chips,
                    matchPoints = it.matchPoints,
                    lastHandPoints = it.lastHandPoints,
                    cardCount = it.hand.size,
                    isDropped = it.isDropped,
                    isEliminated = it.isEliminated,
                    wrongDeclaration = it.wrongDeclaration,
                    hasDrawnThisHand = it.hasDrawnThisHand,
                    connected = true
                )
            },
            mySeatIndex = 0,
            myHand = me.hand.toList(),
            myArrangedGroups = playerArrangement,
            myLastDrawnCardId = me.lastDrawnCardId,
            result = lastResult
        )
    }
}
