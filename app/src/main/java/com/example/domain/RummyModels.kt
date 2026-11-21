package com.example.domain

enum class Suit(val code: String, val symbol: String) {
    SPADES("S", "♠"),
    HEARTS("H", "♥"),
    CLUBS("C", "♣"),
    DIAMONDS("D", "♦"),
    JOKER("JOKER", "★");

    companion object {
        fun fromCode(code: String): Suit = entries.firstOrNull { it.code == code } ?: SPADES
    }
}

data class Card(
    val id: String,
    val deck: Int,
    val suit: Suit,
    val rank: Int, // 0 = Printed Joker, 1 = Ace, 2..13 = 2..K
    val isPrintedJoker: Boolean = false
) {
    val rankLabel: String
        get() = when (rank) {
            0 -> "PJ"
            1 -> "A"
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            else -> rank.toString()
        }

    val isRed: Boolean
        get() = suit == Suit.HEARTS || suit == Suit.DIAMONDS
}

enum class GameMode(val apiCode: String, val totalDeals: Int) {
    POINTS("POINTS", 1),
    POOL_101("POOL_101", 1),
    POOL_201("POOL_201", 1),
    DEALS_3("DEALS_3", 3),
    DEALS_6("DEALS_6", 6);

    companion object {
        fun fromCode(code: String): GameMode = entries.firstOrNull { it.apiCode == code } ?: POINTS
    }
}

enum class GroupType {
    PURE_SEQUENCE,
    IMPURE_SEQUENCE,
    SET,
    INVALID
}

data class GroupValidationItem(
    val type: GroupType,
    val cards: List<Card>
)

data class DeclarationValidation(
    val isValid: Boolean,
    val pureSeqCount: Int,
    val impureSeqCount: Int,
    val setCount: Int,
    val groupResults: List<GroupValidationItem>,
    val reasonEn: String,
    val reasonHi: String
)

data class DropPenaltyInfo(
    val allowed: Boolean,
    val points: Int,
    val reasonEn: String,
    val reasonHi: String
)

data class SettlementAmounts(
    val grossReward: Int,
    val commission: Int,
    val netReward: Int
)

enum class TableStatus(val code: String) {
    WAITING("WAITING"),
    PLAYING("PLAYING"),
    HAND_RESULT("HAND_RESULT"),
    MATCH_RESULT("MATCH_RESULT");

    companion object {
        fun fromCode(code: String): TableStatus = entries.firstOrNull { it.code == code } ?: WAITING
    }
}

enum class TurnPhase(val code: String) {
    DRAW("DRAW"),
    DISCARD("DISCARD");

    companion object {
        fun fromCode(code: String): TurnPhase = entries.firstOrNull { it.code == code } ?: DRAW
    }
}

enum class ConnectionStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    OFFLINE
}

data class SeatInfo(
    val playerId: String,
    val name: String,
    val avatarId: Int,
    val seatIndex: Int,
    val chips: Int,
    val matchPoints: Int,
    val lastHandPoints: Int,
    val cardCount: Int,
    val isDropped: Boolean,
    val isEliminated: Boolean,
    val wrongDeclaration: Boolean,
    val hasDrawnThisHand: Boolean,
    val connected: Boolean
)

data class PlayerScoreRow(
    val playerId: String,
    val name: String,
    val avatarId: Int,
    val handPoints: Int,
    val matchPoints: Int,
    val isWinner: Boolean,
    val isDropped: Boolean,
    val wrongDeclaration: Boolean
)

data class HandResultSummary(
    val handWinnerId: String,
    val handWinnerName: String,
    val matchWinnerId: String?,
    val matchWinnerName: String?,
    val matchFinished: Boolean,
    val dealNumber: Int,
    val totalDeals: Int,
    val winningGroups: List<List<Card>>,
    val handScores: List<PlayerScoreRow>,
    val grossReward: Int,
    val commission: Int,
    val netReward: Int
)

data class TableSnapshot(
    val tableId: String,
    val mode: GameMode,
    val maxSeats: Int,
    val stake: Int,
    val dealNumber: Int,
    val totalDeals: Int,
    val dealSequenceId: Int,
    val status: TableStatus,
    val countdownEndsAtMs: Long?,
    val activeTurnSeatIndex: Int,
    val turnPhase: TurnPhase,
    val drawnFromOpenCardId: String?,
    val jokerIndicator: Card?,
    val wildRank: Int,
    val openPileTop: Card?,
    val openPileCount: Int,
    val closedStockCount: Int,
    val seats: List<SeatInfo>,
    val mySeatIndex: Int,
    val myHand: List<Card>,
    val myArrangedGroups: List<List<String>>,
    val myLastDrawnCardId: String?,
    val result: HandResultSummary?
)

data class FinishReviewState(
    val finishCard: Card,
    val remainingGroups: List<List<String>>,
    val validation: DeclarationValidation
)
