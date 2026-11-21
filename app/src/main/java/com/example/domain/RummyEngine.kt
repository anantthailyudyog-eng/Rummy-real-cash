package com.example.domain

import java.security.SecureRandom
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class DealtHand(
    val hands: List<List<Card>>,
    val jokerIndicator: Card,
    val wildRank: Int,
    val openPile: MutableList<Card>,
    val closedStock: MutableList<Card>
)

object RummyEngine {
    private val secureRandom = SecureRandom()
    private val standardSuits = listOf(Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS)

    fun createDeck106(): List<Card> {
        val cards = ArrayList<Card>(106)
        for (deck in 0..1) {
            for (suit in standardSuits) {
                for (rank in 1..13) {
                    cards.add(
                        Card(
                            id = "d${deck}_${suit.code}_$rank",
                            deck = deck,
                            suit = suit,
                            rank = rank,
                            isPrintedJoker = false
                        )
                    )
                }
            }
            cards.add(
                Card(
                    id = "d${deck}_PJ_0",
                    deck = deck,
                    suit = Suit.JOKER,
                    rank = 0,
                    isPrintedJoker = true
                )
            )
        }
        return cards
    }

    fun secureShuffle(cards: List<Card>): MutableList<Card> {
        val arr = cards.toMutableList()
        for (i in arr.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = arr[i]
            arr[i] = arr[j]
            arr[j] = tmp
        }
        return arr
    }

    fun computeWildRank(jokerIndicator: Card?): Int {
        if (jokerIndicator == null || jokerIndicator.isPrintedJoker || jokerIndicator.rank == 0) {
            return 1 // Printed Joker opens -> all Aces + remaining Printed Joker are wild
        }
        return jokerIndicator.rank
    }

    fun isWildCard(card: Card, wildRank: Int): Boolean {
        return card.isPrintedJoker || card.rank == 0 || card.rank == wildRank
    }

    fun cardPointValue(card: Card, wildRank: Int): Int {
        if (isWildCard(card, wildRank)) return 0
        return if (card.rank == 1 || card.rank >= 10) 10 else card.rank
    }

    fun dealHandFromDeck(shuffledDeck: List<Card>, playerCount: Int): DealtHand {
        require(playerCount in 2..6) { "Invalid player count: $playerCount" }
        val hands = List(playerCount) { ArrayList<Card>(13) }
        var idx = 0
        for (c in 0 until 13) {
            for (p in 0 until playerCount) {
                hands[p].add(shuffledDeck[idx++])
            }
        }
        val indicator = shuffledDeck[idx++]
        val wildRank = computeWildRank(indicator)
        val firstOpen = shuffledDeck[idx++]
        val closedStock = shuffledDeck.subList(idx, shuffledDeck.size).toMutableList()
        return DealtHand(
            hands = hands,
            jokerIndicator = indicator,
            wildRank = wildRank,
            openPile = mutableListOf(firstOpen),
            closedStock = closedStock
        )
    }

    private fun isConsecutive(sortedRanks: List<Int>): Boolean {
        for (i in 1 until sortedRanks.size) {
            if (sortedRanks[i] != sortedRanks[i - 1] + 1) return false
        }
        return true
    }

    fun isPureSequence(cards: List<Card>): Boolean {
        if (cards.size < 3 || cards.size > 13) return false
        if (cards.any { it.isPrintedJoker || it.rank == 0 }) return false
        val suit = cards[0].suit
        if (suit == Suit.JOKER || cards.any { it.suit != suit }) return false

        val ranksLow = cards.map { it.rank }.sorted()
        if (isConsecutive(ranksLow)) return true

        // Ace high (Q-K-A, 10-J-Q-K-A). K-A-2 gives [2, 13, 14] -> rejected!
        if (ranksLow.contains(1)) {
            val ranksHigh = cards.map { if (it.rank == 1) 14 else it.rank }.sorted()
            if (isConsecutive(ranksHigh)) return true
        }
        return false
    }

    fun isImpureSequence(cards: List<Card>, wildRank: Int): Boolean {
        if (cards.size < 3 || cards.size > 13) return false
        if (isPureSequence(cards)) return false

        val naturals = cards.filter { !isWildCard(it, wildRank) }
        val jokerCount = cards.size - naturals.size
        if (naturals.isEmpty()) return true

        val suit = naturals[0].suit
        if (suit == Suit.JOKER || naturals.any { it.suit != suit }) return false

        val uniqueRanks = naturals.map { it.rank }.toSet()
        if (uniqueRanks.size != naturals.size) return false

        val totalLen = cards.size
        val lowRanks = uniqueRanks.sorted()
        val lowSpan = lowRanks.last() - lowRanks.first() + 1
        if (lowSpan <= totalLen && jokerCount >= lowSpan - naturals.size) return true

        if (uniqueRanks.contains(1)) {
            val highRanks = naturals.map { if (it.rank == 1) 14 else it.rank }.sorted()
            val highSpan = highRanks.last() - highRanks.first() + 1
            if (highSpan <= totalLen && jokerCount >= highSpan - naturals.size) return true
        }
        return false
    }

    fun isSet(cards: List<Card>, wildRank: Int): Boolean {
        if (cards.size !in 3..4) return false
        val naturals = cards.filter { !isWildCard(it, wildRank) }
        if (naturals.isEmpty()) return true

        val targetRank = naturals[0].rank
        if (naturals.any { it.rank != targetRank }) return false

        val distinctSuits = naturals.map { it.suit }.toSet()
        return distinctSuits.size == naturals.size
    }

    fun classifyGroup(cards: List<Card>, wildRank: Int): GroupType {
        if (cards.isEmpty()) return GroupType.INVALID
        if (isPureSequence(cards)) return GroupType.PURE_SEQUENCE
        if (isImpureSequence(cards, wildRank)) return GroupType.IMPURE_SEQUENCE
        if (isSet(cards, wildRank)) return GroupType.SET
        return GroupType.INVALID
    }

    fun validateDeclaration(
        groups: List<List<Card>>,
        wildRank: Int,
        expectedHand: List<Card>? = null
    ): DeclarationValidation {
        val flat = groups.flatten()
        if (flat.size != 13) {
            return DeclarationValidation(
                isValid = false,
                pureSeqCount = 0,
                impureSeqCount = 0,
                setCount = 0,
                groupResults = groups.map { GroupValidationItem(classifyGroup(it, wildRank), it) },
                reasonEn = "Declaration must contain exactly 13 remaining cards (found ${flat.size}).",
                reasonHi = "डिक्लेरेशन में ठीक 13 कार्ड होने चाहिए (अभी ${flat.size} हैं)।"
            )
        }

        val seenIds = flat.map { it.id }.toSet()
        if (seenIds.size != 13) {
            return DeclarationValidation(
                isValid = false,
                pureSeqCount = 0,
                impureSeqCount = 0,
                setCount = 0,
                groupResults = emptyList(),
                reasonEn = "Duplicate physical card detected in declaration.",
                reasonHi = "डिक्लेरेशन में डुप्लिकेट कार्ड पाया गया।"
            )
        }

        if (expectedHand != null) {
            val expectedIds = expectedHand.map { it.id }.toSet()
            if (expectedIds != seenIds) {
                return DeclarationValidation(
                    isValid = false,
                    pureSeqCount = 0,
                    impureSeqCount = 0,
                    setCount = 0,
                    groupResults = emptyList(),
                    reasonEn = "Submitted cards do not match your actual hand.",
                    reasonHi = "भेजे गए कार्ड आपके वास्तविक हाथ से मेल नहीं खाते।"
                )
            }
        }

        var pureCount = 0
        var impureCount = 0
        var setCount = 0
        var invalidCount = 0

        val results = groups.map { g ->
            val type = classifyGroup(g, wildRank)
            when (type) {
                GroupType.PURE_SEQUENCE -> pureCount++
                GroupType.IMPURE_SEQUENCE -> impureCount++
                GroupType.SET -> setCount++
                GroupType.INVALID -> invalidCount++
            }
            GroupValidationItem(type, g)
        }

        if (invalidCount > 0) {
            return DeclarationValidation(
                isValid = false,
                pureSeqCount = pureCount,
                impureSeqCount = impureCount,
                setCount = setCount,
                groupResults = results,
                reasonEn = "$invalidCount group(s) are not valid sequences or sets (K-A-2 and duplicate suits in sets are invalid).",
                reasonHi = "$invalidCount समूह वैध सीक्वेंस या सेट नहीं हैं (K-A-2 और सेट में समान सूट अमान्य हैं)।"
            )
        }

        if (pureCount < 1) {
            return DeclarationValidation(
                isValid = false,
                pureSeqCount = pureCount,
                impureSeqCount = impureCount,
                setCount = setCount,
                groupResults = results,
                reasonEn = "At least 1 Pure Sequence (no jokers) is required.",
                reasonHi = "कम से कम 1 प्योर सीक्वेंस (बिना जोकर) आवश्यक है।"
            )
        }

        if (pureCount + impureCount < 2) {
            return DeclarationValidation(
                isValid = false,
                pureSeqCount = pureCount,
                impureSeqCount = impureCount,
                setCount = setCount,
                groupResults = results,
                reasonEn = "At least 2 Sequences (including 1 Pure Sequence) are required.",
                reasonHi = "कम से कम 2 सीक्वेंस (1 प्योर सीक्वेंस सहित) आवश्यक हैं।"
            )
        }

        return DeclarationValidation(
            isValid = true,
            pureSeqCount = pureCount,
            impureSeqCount = impureCount,
            setCount = setCount,
            groupResults = results,
            reasonEn = "Valid declaration! Pure Sequence, Second Sequence, and all melds verified.",
            reasonHi = "वैध डिक्लेरेशन! प्योर सीक्वेंस, दूसरा सीक्वेंस और सभी समूह सही हैं।"
        )
    }

    fun calculateDeadwoodPoints(hand: List<Card>, wildRank: Int): Int {
        if (hand.isEmpty()) return 0
        val n = hand.size
        val cardPts = hand.map { cardPointValue(it, wildRank) }
        val totalPts = min(80, cardPts.sum())

        val pureMasks = ArrayList<Int>()
        val impureMasks = ArrayList<Int>()
        val setMasks = ArrayList<Int>()

        val bySuit = mutableMapOf<Suit, MutableList<Int>>()
        val wildIndices = ArrayList<Int>()
        val nonWildIndices = ArrayList<Int>()

        for (i in 0 until n) {
            val c = hand[i]
            if (isWildCard(c, wildRank)) wildIndices.add(i) else nonWildIndices.add(i)
            if (!c.isPrintedJoker && c.suit != Suit.JOKER) {
                bySuit.getOrPut(c.suit) { ArrayList() }.add(i)
            }
        }

        for (suit in standardSuits) {
            val idxs = bySuit[suit] ?: continue
            if (idxs.size < 3) continue
            val m = idxs.size
            for (mask in 0 until (1 shl m)) {
                val bits = Integer.bitCount(mask)
                if (bits !in 3..7) continue
                val sub = ArrayList<Card>(bits)
                var fullMask = 0
                for (b in 0 until m) {
                    if ((mask and (1 shl b)) != 0) {
                        sub.add(hand[idxs[b]])
                        fullMask = fullMask or (1 shl idxs[b])
                    }
                }
                if (isPureSequence(sub)) pureMasks.add(fullMask)
            }
        }

        if (pureMasks.isEmpty()) return totalPts

        val nonWildBySuit = mutableMapOf<Suit, MutableList<Int>>()
        val nonWildByRank = mutableMapOf<Int, MutableList<Int>>()
        for (i in nonWildIndices) {
            val c = hand[i]
            nonWildBySuit.getOrPut(c.suit) { ArrayList() }.add(i)
            nonWildByRank.getOrPut(c.rank) { ArrayList() }.add(i)
        }

        val wildMasks = ArrayList<Int>()
        wildMasks.add(0)
        for (wm in 1 until (1 shl wildIndices.size)) {
            var fullW = 0
            for (b in wildIndices.indices) {
                if ((wm and (1 shl b)) != 0) fullW = fullW or (1 shl wildIndices[b])
            }
            wildMasks.add(fullW)
        }

        for (suit in standardSuits) {
            val idxs = nonWildBySuit[suit] ?: continue
            val m = idxs.size
            for (mask in 1 until (1 shl m)) {
                if (Integer.bitCount(mask) > 6) continue
                var natMask = 0
                for (b in 0 until m) {
                    if ((mask and (1 shl b)) != 0) natMask = natMask or (1 shl idxs[b])
                }
                for (wMask in wildMasks) {
                    if (wMask == 0) continue
                    val combined = natMask or wMask
                    val size = Integer.bitCount(combined)
                    if (size !in 3..6) continue
                    val cards = maskToCards(hand, combined)
                    if (isImpureSequence(cards, wildRank)) impureMasks.add(combined)
                }
            }
        }

        for (idxs in nonWildByRank.values) {
            val m = idxs.size
            for (mask in 1 until (1 shl m)) {
                if (Integer.bitCount(mask) > 4) continue
                var natMask = 0
                for (b in 0 until m) {
                    if ((mask and (1 shl b)) != 0) natMask = natMask or (1 shl idxs[b])
                }
                for (wMask in wildMasks) {
                    val combined = natMask or wMask
                    val size = Integer.bitCount(combined)
                    if (size !in 3..4) continue
                    val cards = maskToCards(hand, combined)
                    if (isSet(cards, wildRank)) setMasks.add(combined)
                }
            }
        }

        var bestPoints = totalPts
        for (pMask in pureMasks) {
            val pts = sumUnmaskedPoints(cardPts, n, pMask)
            if (pts < bestPoints) bestPoints = pts
        }

        val allSecondSeqs = pureMasks + impureMasks
        val allAdditional = pureMasks + impureMasks + setMasks
        val memo = HashMap<Int, Int>()

        fun solveAdditional(usedMask: Int): Int {
            memo[usedMask]?.let { return it }
            var minPts = sumUnmaskedPoints(cardPts, n, usedMask)
            for (mMask in allAdditional) {
                if ((usedMask and mMask) == 0) {
                    val sub = solveAdditional(usedMask or mMask)
                    if (sub < minPts) minPts = sub
                    if (minPts == 0) break
                }
            }
            memo[usedMask] = minPts
            return minPts
        }

        for (pMask in pureMasks) {
            for (sMask in allSecondSeqs) {
                if ((pMask and sMask) == 0) {
                    val pts = solveAdditional(pMask or sMask)
                    if (pts < bestPoints) bestPoints = pts
                    if (bestPoints == 0) return 0
                }
            }
        }

        return min(80, bestPoints)
    }

    private fun maskToCards(hand: List<Card>, mask: Int): List<Card> {
        val list = ArrayList<Card>()
        for (i in hand.indices) {
            if ((mask and (1 shl i)) != 0) list.add(hand[i])
        }
        return list
    }

    private fun sumUnmaskedPoints(cardPts: List<Int>, n: Int, usedMask: Int): Int {
        var sum = 0
        for (i in 0 until n) {
            if ((usedMask and (1 shl i)) == 0) sum += cardPts[i]
        }
        return sum
    }

    fun sortHandBySuitAndRank(cards: List<Card>): List<List<Card>> {
        val bySuit = linkedMapOf(
            Suit.SPADES to mutableListOf<Card>(),
            Suit.HEARTS to mutableListOf(),
            Suit.CLUBS to mutableListOf(),
            Suit.DIAMONDS to mutableListOf()
        )
        val pjs = mutableListOf<Card>()
        for (c in cards) {
            if (c.isPrintedJoker || c.rank == 0 || c.suit == Suit.JOKER) {
                pjs.add(c)
            } else {
                bySuit[c.suit]?.add(c)
            }
        }
        val groups = mutableListOf<List<Card>>()
        for (list in bySuit.values) {
            if (list.isNotEmpty()) {
                groups.add(list.sortedWith(compareBy<Card> { it.rank }.thenBy { it.deck }))
            }
        }
        if (pjs.isNotEmpty()) {
            groups.add(pjs.sortedBy { it.deck })
        }
        return groups
    }

    fun computeCommissionAndNet(grossReward: Int): SettlementAmounts {
        val gross = max(0, grossReward)
        val commission = floor(gross * 0.10).toInt()
        return SettlementAmounts(grossReward = gross, commission = commission, netReward = gross - commission)
    }

    fun getDropPenalty(mode: GameMode, hasDrawnInHand: Boolean): DropPenaltyInfo {
        if (mode == GameMode.DEALS_3 || mode == GameMode.DEALS_6) {
            return DropPenaltyInfo(
                allowed = false,
                points = 0,
                reasonEn = "Drop is not allowed in Deals Rummy.",
                reasonHi = "डील्स रमी में ड्रॉप की अनुमति नहीं है।"
            )
        }
        if (mode == GameMode.POOL_201) {
            val pts = if (hasDrawnInHand) 50 else 25
            return DropPenaltyInfo(
                allowed = true,
                points = pts,
                reasonEn = if (hasDrawnInHand) "Middle Drop: 50 pts penalty" else "First Drop: 25 pts penalty",
                reasonHi = if (hasDrawnInHand) "मिडिल ड्रॉप: 50 अंक दंड" else "फर्स्ट ड्रॉप: 25 अंक दंड"
            )
        }
        val pts = if (hasDrawnInHand) 40 else 20
        return DropPenaltyInfo(
            allowed = true,
            points = pts,
            reasonEn = if (hasDrawnInHand) "Middle Drop: 40 pts penalty" else "First Drop: 20 pts penalty",
            reasonHi = if (hasDrawnInHand) "मिडिल ड्रॉप: 40 अंक दंड" else "फर्स्ट ड्रॉप: 20 अंक दंड"
        )
    }
}
