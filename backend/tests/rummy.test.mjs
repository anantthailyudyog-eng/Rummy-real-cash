import test from 'node:test';
import assert from 'node:assert/strict';
import {
  GAME_MODES,
  createDeck106,
  computeWildRank,
  isWildCard,
  isPureSequence,
  isImpureSequence,
  isSet,
  validateDeclaration,
  sortHandBySuitAndRank,
  computeCommissionAndNet,
  calculateDeadwoodPoints,
} from '../engine/rummyEngine.mjs';
import { createRummyServer } from '../server.mjs';

function makeCard(deck, suit, rank) {
  if (suit === 'JOKER' || rank === 0) {
    return { id: `d${deck}_PJ_0`, deck, suit: 'JOKER', rank: 0, isPrintedJoker: true };
  }
  return { id: `d${deck}_${suit}_${rank}`, deck, suit, rank, isPrintedJoker: false };
}

test('106-card deck has unique IDs including duplicate rank/suit copies and 2 printed jokers', () => {
  const deck = createDeck106();
  assert.equal(deck.length, 106);
  const ids = new Set(deck.map((c) => c.id));
  assert.equal(ids.size, 106);
  const pjs = deck.filter((c) => c.isPrintedJoker);
  assert.equal(pjs.length, 2);
});

test('Joker indicator cases: 2♦ opens vs Printed Joker opens', () => {
  const twoDiamonds = makeCard(0, 'D', 2);
  const wildFrom2D = computeWildRank(twoDiamonds);
  assert.equal(wildFrom2D, 2);
  assert.equal(isWildCard(makeCard(0, 'S', 2), wildFrom2D), true);
  assert.equal(isWildCard(makeCard(0, 'H', 2), wildFrom2D), true);
  assert.equal(isWildCard(makeCard(0, 'PJ', 0), wildFrom2D), true);
  assert.equal(isWildCard(makeCard(0, 'H', 3), wildFrom2D), false);

  const printedJoker = makeCard(0, 'JOKER', 0);
  const wildFromPJ = computeWildRank(printedJoker);
  assert.equal(wildFromPJ, 1);
  assert.equal(isWildCard(makeCard(0, 'S', 1), wildFromPJ), true);
  assert.equal(isWildCard(makeCard(0, 'H', 1), wildFromPJ), true);
  assert.equal(isWildCard(makeCard(1, 'JOKER', 0), wildFromPJ), true);
  assert.equal(isWildCard(makeCard(0, 'D', 2), wildFromPJ), false);
});

test('Sequence & Set validation: A-2-3, Q-K-A valid; K-A-2 and duplicate-suit set rejected', () => {
  assert.equal(isPureSequence([makeCard(0, 'S', 1), makeCard(0, 'S', 2), makeCard(0, 'S', 3)]), true);
  assert.equal(isPureSequence([makeCard(0, 'H', 12), makeCard(0, 'H', 13), makeCard(0, 'H', 1)]), true);
  // K-A-2 rejected in both pure and impure
  assert.equal(isPureSequence([makeCard(0, 'S', 13), makeCard(0, 'S', 1), makeCard(0, 'S', 2)]), false);
  assert.equal(isImpureSequence([makeCard(0, 'S', 13), makeCard(0, 'S', 1), makeCard(0, 'S', 2), makeCard(0, 'JOKER', 0)], 7), false);

  // Set cannot contain duplicate suits of natural cards
  assert.equal(isSet([makeCard(0, 'H', 7), makeCard(1, 'H', 7), makeCard(0, 'S', 7)], 5), false);
  assert.equal(isSet([makeCard(0, 'H', 7), makeCard(0, 'C', 7), makeCard(0, 'S', 7)], 5), true);
  assert.equal(isSet([makeCard(0, 'H', 7), makeCard(0, 'C', 7), makeCard(0, 'JOKER', 0)], 5), true);

  // Full declaration requires 1 pure seq + 1 other seq + valid remaining melds
  const validGroups = [
    [makeCard(0, 'S', 1), makeCard(0, 'S', 2), makeCard(0, 'S', 3)], // Pure
    [makeCard(0, 'H', 5), makeCard(0, 'H', 6), makeCard(0, 'JOKER', 0)], // Impure
    [makeCard(0, 'D', 9), makeCard(0, 'C', 9), makeCard(0, 'S', 9)], // Set
    [makeCard(0, 'D', 11), makeCard(0, 'D', 12), makeCard(0, 'D', 13), makeCard(0, 'D', 1)], // Pure 4
  ];
  assert.equal(validateDeclaration(validGroups, 4).isValid, true);

  // Missing pure sequence -> invalid
  const noPure = [
    [makeCard(0, 'S', 1), makeCard(0, 'S', 2), makeCard(0, 'JOKER', 0)],
    [makeCard(0, 'H', 5), makeCard(0, 'H', 7), makeCard(1, 'JOKER', 0)],
    [makeCard(0, 'D', 9), makeCard(0, 'C', 9), makeCard(0, 'S', 9)],
    [makeCard(0, 'C', 11), makeCard(0, 'D', 11), makeCard(0, 'S', 11), makeCard(0, 'H', 11)],
  ];
  assert.equal(validateDeclaration(noPure, 4).isValid, false);
});

test('Sorting previously grouped hand and moving one of two identical cards', () => {
  const c1 = makeCard(0, 'H', 7);
  const c2 = makeCard(1, 'H', 7);
  const hand = [c2, makeCard(0, 'S', 10), c1, makeCard(0, 'S', 2), makeCard(0, 'JOKER', 0)];
  const sorted = sortHandBySuitAndRank(hand);
  assert.equal(sorted.length, 3); // S, H, PJ
  assert.deepEqual(sorted[0].map((c) => c.id), ['d0_S_2', 'd0_S_10']);
  assert.deepEqual(sorted[1].map((c) => c.id), ['d0_H_7', 'd1_H_7']);
  assert.deepEqual(sorted[2].map((c) => c.id), ['d0_PJ_0']);
  // Distinct IDs allow moving d0_H_7 independently of d1_H_7
  assert.notEqual(sorted[1][0].id, sorted[1][1].id);
});

test('10% commission deduction and deadwood calculation', () => {
  const { grossReward, commission, netReward } = computeCommissionAndNet(100);
  assert.equal(grossReward, 100);
  assert.equal(commission, 10);
  assert.equal(netReward, 90);

  const handWithPure = [
    makeCard(0, 'S', 1),
    makeCard(0, 'S', 2),
    makeCard(0, 'S', 3),
    makeCard(0, 'H', 13),
    makeCard(0, 'D', 12),
  ];
  // Pure sequence A-2-3 is 0 pts, K+Q = 20 pts
  assert.equal(calculateDeadwoodPoints(handWithPure, 7), 20);
});

test('Two-session multiplayer: 20s deadline enforcement, 2/4/6 seats, lone waiting, late arrival, reconnect, and idempotent settlement', async () => {
  // Controlled deck so Player 0 can finish cleanly
  const controlledDeckProvider = () => {
    const all = createDeck106();
    const p0Target = [
      makeCard(0, 'S', 1),
      makeCard(0, 'S', 2),
      makeCard(0, 'S', 3),
      makeCard(0, 'H', 4),
      makeCard(0, 'H', 5),
      makeCard(0, 'H', 6),
      makeCard(0, 'D', 8),
      makeCard(0, 'C', 8),
      makeCard(0, 'S', 8),
      makeCard(0, 'C', 10),
      makeCard(0, 'C', 11),
      makeCard(0, 'C', 12),
      makeCard(0, 'D', 13), // 13th card (will swap or discard)
    ];
    const usedIds = new Set(p0Target.map((c) => c.id));
    const indicator = makeCard(0, 'D', 2); // 2♦ opens -> all 2s + PJ wild!
    usedIds.add(indicator.id);
    const firstOpen = makeCard(0, 'C', 13); // K♣ on open pile for P0 to draw and form 10-J-Q-K!
    usedIds.add(firstOpen.id);

    const rest = all.filter((c) => !usedIds.has(c.id));
    // Interleave P0 and P1 for 13 rounds (dealHandFromDeck deals round-robin)
    const deck = [];
    for (let i = 0; i < 13; i++) {
      deck.push(p0Target[i]);
      deck.push(rest[i]);
    }
    deck.push(indicator);
    deck.push(firstOpen);
    deck.push(...rest.slice(13));
    return deck;
  };

  // Use 350ms countdown in test harness to verify real server timer behavior deterministically without waiting 20s per test
  const instance = createRummyServer({ countdownMs: 350, deckProvider: controlledDeckProvider });
  await new Promise((resolve) => instance.server.listen(0, '127.0.0.1', resolve));
  const port = instance.server.address().port;
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    // 1. Lone player test (on a 3-seat table)
    const loneRes = await fetch(`${baseUrl}/api/tables/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: 'lone_1', name: 'LoneOne', mode: 'POINTS', maxSeats: 3, stake: 100 }),
    });
    const loneJoin = await loneRes.json();
    assert.equal(loneJoin.state.status, 'WAITING');
    await new Promise((r) => setTimeout(r, 450));
    const loneCheck = await (await fetch(`${baseUrl}/api/tables/${loneJoin.state.tableId}/state?playerId=lone_1`)).json();
    assert.equal(loneCheck.state.status, 'WAITING');
    assert.equal(loneCheck.state.myHand.length, 0);

    // Clean up lone player
    await fetch(`${baseUrl}/api/tables/leave`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: 'lone_1' }),
    });

    // 2. Two independent players on a 6-seat table
    const p1Join = await (
      await fetch(`${baseUrl}/api/tables/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId: 'p1', name: 'Asha', mode: 'POINTS', maxSeats: 6, stake: 100, chips: 5000 }),
      })
    ).json();
    const p2Join = await (
      await fetch(`${baseUrl}/api/tables/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId: 'p2', name: 'Vikram', mode: 'POINTS', maxSeats: 6, stake: 100, chips: 5000 }),
      })
    ).json();

    assert.equal(p1Join.state.tableId, p2Join.state.tableId);
    // Before countdown deadline: NO cards dealt!
    assert.equal(p1Join.state.status, 'WAITING');
    assert.equal(p1Join.state.myHand.length, 0);
    assert.equal(p2Join.state.myHand.length, 0);

    // Wait past countdown deadline
    await new Promise((r) => setTimeout(r, 450));

    const p1State = (await (await fetch(`${baseUrl}/api/tables/${p1Join.state.tableId}/state?playerId=p1`)).json()).state;
    const p2State = (await (await fetch(`${baseUrl}/api/tables/${p1Join.state.tableId}/state?playerId=p2`)).json()).state;

    assert.equal(p1State.status, 'PLAYING');
    assert.equal(p1State.myHand.length, 13);
    assert.equal(p2State.myHand.length, 13);
    assert.equal(p1State.jokerIndicator.id, 'd0_D_2');
    assert.equal(p1State.wildRank, 2);

    // Hands must be private (non-overlapping physical IDs)
    const p1Ids = new Set(p1State.myHand.map((c) => c.id));
    for (const c of p2State.myHand) assert.equal(p1Ids.has(c.id), false);

    // Late arrival goes to NEXT table
    const p3Late = await (
      await fetch(`${baseUrl}/api/tables/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId: 'p3', name: 'LateUser', mode: 'POINTS', maxSeats: 6, stake: 100 }),
      })
    ).json();
    assert.notEqual(p3Late.state.tableId, p1Join.state.tableId);
    assert.equal(p3Late.state.status, 'WAITING');

    // Save custom arrangement and test reconnect preservation
    const customGroups = [
      ['d0_S_1', 'd0_S_2', 'd0_S_3'],
      ['d0_H_4', 'd0_H_5', 'd0_H_6'],
      ['d0_D_8', 'd0_C_8', 'd0_S_8'],
      ['d0_C_10', 'd0_C_11', 'd0_C_12', 'd0_D_13'],
    ];
    await fetch(`${baseUrl}/api/tables/${p1State.tableId}/action`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: 'p1', type: 'SAVE_ARRANGEMENT', groups: customGroups }),
    });

    const p1Reconnected = await (
      await fetch(`${baseUrl}/api/tables/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          playerId: 'p1',
          reconnectToken: p1Join.reconnectToken,
          mode: 'POINTS',
          maxSeats: 6,
          stake: 100,
        }),
      })
    ).json();
    assert.equal(p1Reconnected.reconnected, true);
    assert.deepEqual(p1Reconnected.state.myArrangedGroups, customGroups);

    // Draw K♣ from open pile -> verify immediate discard of K♣ is blocked!
    const afterOpen = await (
      await fetch(`${baseUrl}/api/tables/${p1State.tableId}/action`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId: 'p1', type: 'DRAW_OPEN' }),
      })
    ).json();
    assert.equal(afterOpen.state.myHand.length, 14);

    const badDiscard = await fetch(`${baseUrl}/api/tables/${p1State.tableId}/action`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: 'p1', type: 'DISCARD', cardId: 'd0_C_13' }),
    });
    assert.equal(badDiscard.status, 400);

    // Submit valid finish discarding d0_D_13 with 4 valid groups!
    const finishGroups = [
      ['d0_S_1', 'd0_S_2', 'd0_S_3'],
      ['d0_H_4', 'd0_H_5', 'd0_H_6'],
      ['d0_D_8', 'd0_C_8', 'd0_S_8'],
      ['d0_C_10', 'd0_C_11', 'd0_C_12', 'd0_C_13'],
    ];
    const winRes = await (
      await fetch(`${baseUrl}/api/tables/${p1State.tableId}/action`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          playerId: 'p1',
          type: 'SUBMIT_FINISH',
          finishCardId: 'd0_D_13',
          groups: finishGroups,
        }),
      })
    ).json();
    assert.equal(winRes.state.status, 'MATCH_RESULT');
    assert.equal(winRes.state.result.handWinnerId, 'p1');
    const chipsAfterWin = winRes.state.seats.find((s) => s.playerId === 'p1').chips;
    assert.equal(chipsAfterWin, 5000 + winRes.state.result.netReward);
    assert.equal(winRes.state.result.commission, Math.floor(winRes.state.result.grossReward * 0.1));

    // Attempt duplicate finish submission -> must NOT award chips twice!
    const dupRes = await fetch(`${baseUrl}/api/tables/${p1State.tableId}/action`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        playerId: 'p1',
        type: 'SUBMIT_FINISH',
        finishCardId: 'd0_D_13',
        groups: finishGroups,
      }),
    });
    assert.equal(dupRes.status, 400);
    const p1Final = (await (await fetch(`${baseUrl}/api/tables/${p1State.tableId}/state?playerId=p1`)).json()).state;
    assert.equal(p1Final.seats.find((s) => s.playerId === 'p1').chips, chipsAfterWin);
  } finally {
    instance.closeAll();
  }
});

test('Six players arriving early at 6-seat table wait until deadline and Printed Joker indicator makes Aces wild', async () => {
  const pjDeckProvider = () => {
    const all = createDeck106();
    const indicator = makeCard(0, 'JOKER', 0);
    const rest = all.filter((c) => c.id !== indicator.id);
    // 6 players * 13 cards = 78 cards, then indicator at index 78, then open card + closed stock
    return [...rest.slice(0, 78), indicator, ...rest.slice(78)];
  };
  const instance = createRummyServer({ countdownMs: 300, deckProvider: pjDeckProvider });
  await new Promise((resolve) => instance.server.listen(0, '127.0.0.1', resolve));
  const port = instance.server.address().port;
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    const joined = [];
    for (let i = 0; i < 6; i++) {
      const res = await (
        await fetch(`${baseUrl}/api/tables/join`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ playerId: `s6_${i}`, name: `P${i}`, mode: GAME_MODES.POOL_101, maxSeats: 6, stake: 100 }),
        })
      ).json();
      joined.push(res.state);
    }
    // All 6 share the same table and are STILL waiting before deadline
    assert.equal(new Set(joined.map((s) => s.tableId)).size, 1);
    assert.equal(joined[5].seats.length, 6);
    assert.equal(joined[5].status, 'WAITING');

    await new Promise((r) => setTimeout(r, 380));
    const afterDeal = (await (await fetch(`${baseUrl}/api/tables/${joined[0].tableId}/state?playerId=s6_0`)).json()).state;
    assert.equal(afterDeal.status, 'PLAYING');
    assert.equal(afterDeal.seats.length, 6);
    assert.equal(afterDeal.jokerIndicator.isPrintedJoker, true);
    assert.equal(afterDeal.wildRank, 1); // All Aces wild!
  } finally {
    instance.closeAll();
  }
});
