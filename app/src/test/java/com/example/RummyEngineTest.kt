package com.example

import com.example.domain.Card
import com.example.domain.CpuPracticeEngine
import com.example.domain.GameMode
import com.example.domain.RummyEngine
import com.example.domain.Suit
import com.example.domain.TableStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RummyEngineTest {
    private fun card(deck: Int, suit: Suit, rank: Int): Card =
        if (suit == Suit.JOKER || rank == 0) Card("d${deck}_PJ_0", deck, Suit.JOKER, 0, true)
        else Card("d${deck}_${suit.code}_$rank", deck, suit, rank, false)

    @Test
    fun `deck has 106 unique physical cards including 2 printed jokers`() {
        val deck = RummyEngine.createDeck106()
        assertEquals(106, deck.size)
        assertEquals(106, deck.map { it.id }.toSet().size)
        assertEquals(2, deck.count { it.isPrintedJoker })
    }

    @Test
    fun `joker indicator rules for 2 of diamonds and printed joker`() {
        val wild2D = RummyEngine.computeWildRank(card(0, Suit.DIAMONDS, 2))
        assertEquals(2, wild2D)
        assertTrue(RummyEngine.isWildCard(card(0, Suit.SPADES, 2), wild2D))
        assertTrue(RummyEngine.isWildCard(card(1, Suit.JOKER, 0), wild2D))
        assertFalse(RummyEngine.isWildCard(card(0, Suit.HEARTS, 5), wild2D))

        val wildPj = RummyEngine.computeWildRank(card(0, Suit.JOKER, 0))
        assertEquals(1, wildPj)
        assertTrue(RummyEngine.isWildCard(card(0, Suit.HEARTS, 1), wildPj))
        assertTrue(RummyEngine.isWildCard(card(1, Suit.JOKER, 0), wildPj))
        assertFalse(RummyEngine.isWildCard(card(0, Suit.DIAMONDS, 2), wildPj))
    }

    @Test
    fun `sequence and set validation enforces A-2-3 Q-K-A and rejects K-A-2 and duplicate suits`() {
        assertTrue(RummyEngine.isPureSequence(listOf(card(0, Suit.SPADES, 1), card(0, Suit.SPADES, 2), card(0, Suit.SPADES, 3))))
        assertTrue(RummyEngine.isPureSequence(listOf(card(0, Suit.HEARTS, 12), card(0, Suit.HEARTS, 13), card(0, Suit.HEARTS, 1))))
        assertFalse(RummyEngine.isPureSequence(listOf(card(0, Suit.SPADES, 13), card(0, Suit.SPADES, 1), card(0, Suit.SPADES, 2))))
        assertFalse(
            RummyEngine.isImpureSequence(
                listOf(card(0, Suit.SPADES, 13), card(0, Suit.SPADES, 1), card(0, Suit.SPADES, 2), card(0, Suit.JOKER, 0)),
                7
            )
        )

        assertFalse(RummyEngine.isSet(listOf(card(0, Suit.HEARTS, 7), card(1, Suit.HEARTS, 7), card(0, Suit.SPADES, 7)), 5))
        assertTrue(RummyEngine.isSet(listOf(card(0, Suit.HEARTS, 7), card(0, Suit.CLUBS, 7), card(0, Suit.SPADES, 7)), 5))

        val valid = listOf(
            listOf(card(0, Suit.SPADES, 1), card(0, Suit.SPADES, 2), card(0, Suit.SPADES, 3)),
            listOf(card(0, Suit.HEARTS, 5), card(0, Suit.HEARTS, 6), card(0, Suit.JOKER, 0)),
            listOf(card(0, Suit.DIAMONDS, 9), card(0, Suit.CLUBS, 9), card(0, Suit.SPADES, 9)),
            listOf(card(0, Suit.DIAMONDS, 11), card(0, Suit.DIAMONDS, 12), card(0, Suit.DIAMONDS, 13), card(0, Suit.DIAMONDS, 1))
        )
        assertTrue(RummyEngine.validateDeclaration(valid, 4).isValid)
    }

    @Test
    fun `sort arranges across groups and preserves distinct identical cards`() {
        val c1 = card(0, Suit.HEARTS, 7)
        val c2 = card(1, Suit.HEARTS, 7)
        val sorted = RummyEngine.sortHandBySuitAndRank(listOf(c2, card(0, Suit.SPADES, 10), c1, card(0, Suit.SPADES, 2)))
        assertEquals(2, sorted.size)
        assertEquals(listOf("d0_S_2", "d0_S_10"), sorted[0].map { it.id })
        assertEquals(listOf("d0_H_7", "d1_H_7"), sorted[1].map { it.id })
        assertNotEquals(sorted[1][0].id, sorted[1][1].id)
    }

    @Test
    fun `commission deducts 10 percent once and cpu engine blocks immediate open-card re-discard`() {
        val settled = RummyEngine.computeCommissionAndNet(100)
        assertEquals(100, settled.grossReward)
        assertEquals(10, settled.commission)
        assertEquals(90, settled.netReward)

        val engine = CpuPracticeEngine(
            playerId = "u1",
            playerName = "Tester",
            playerAvatarId = 0,
            initialChips = 5000,
            mode = GameMode.POINTS,
            maxSeats = 2
        )
        val initial = engine.snapshot()
        assertEquals(TableStatus.PLAYING, initial.status)
        assertEquals(13, initial.myHand.size)
        val openTopId = initial.openPileTop!!.id
        val afterOpenDraw = engine.drawOpen()
        assertEquals(14, afterOpenDraw.myHand.size)
        assertTrue(engine.discard(openTopId).isFailure)
        val otherCardId = afterOpenDraw.myHand.first { it.id != openTopId }.id
        assertTrue(engine.discard(otherCardId).isSuccess)
    }
}
