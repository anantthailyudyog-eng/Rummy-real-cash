import crypto from 'node:crypto';

export const SUITS = ['S', 'H', 'C', 'D'];
export const GAME_MODES = {
  POINTS: 'POINTS',
  POOL_101: 'POOL_101',
  POOL_201: 'POOL_201',
  DEALS_3: 'DEALS_3',
  DEALS_6: 'DEALS_6',
};

export function createDeck106() {
  const cards = [];
  for (let deck = 0; deck < 2; deck++) {
    for (const suit of SUITS) {
      for (let rank = 1; rank <= 13; rank++) {
        cards.push({
          id: `d${deck}_${suit}_${rank}`,
          deck,
          suit,
          rank,
          isPrintedJoker: false,
        });
      }
    }
    cards.push({
      id: `d${deck}_PJ_0`,
      deck,
      suit: 'JOKER',
      rank: 0,
      isPrintedJoker: true,
    });
  }
  return cards;
}

export function secureShuffle(cards) {
  const arr = [...cards];
  for (let i = arr.length - 1; i > 0; i--) {
    const j = crypto.randomInt(i + 1);
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

export function computeWildRank(jokerIndicator) {
  if (!jokerIndicator) return 1;
  if (jokerIndicator.isPrintedJoker || jokerIndicator.rank === 0) {
    return 1; // Printed Joker opens -> all Aces and remaining Printed Joker are wild
  }
  return jokerIndicator.rank;
}

export function isWildCard(card, wildRank) {
  if (!card) return false;
  return Boolean(card.isPrintedJoker || card.rank === 0 || card.rank === wildRank);
}

export function cardPointValue(card, wildRank) {
  if (!card) return 0;
  if (isWildCard(card, wildRank)) return 0;
  if (card.rank === 1 || card.rank >= 10) return 10;
  return card.rank;
}

export function dealHandFromDeck(shuffledDeck, playerCount) {
  if (playerCount < 2 || playerCount > 6) {
    throw new Error(`Invalid playerCount: ${playerCount}`);
  }
  const hands = Array.from({ length: playerCount }, () => []);
  let idx = 0;
  for (let c = 0; c < 13; c++) {
    for (let p = 0; p < playerCount; p++) {
      hands[p].push(shuffledDeck[idx++]);
    }
  }
  const jokerIndicator = shuffledDeck[idx++];
  const wildRank = computeWildRank(jokerIndicator);
  const firstOpenCard = shuffledDeck[idx++];
  const closedStock = shuffledDeck.slice(idx);
  return {
    hands,
    jokerIndicator,
    wildRank,
    openPile: [firstOpenCard],
    closedStock,
  };
}

function isConsecutiveList(sortedRanks) {
  for (let i = 1; i < sortedRanks.length; i++) {
    if (sortedRanks[i] !== sortedRanks[i - 1] + 1) return false;
  }
  return true;
}

export function isPureSequence(cards) {
  if (!cards || cards.length < 3 || cards.length > 13) return false;
  if (cards.some((c) => c.isPrintedJoker || c.rank === 0)) return false;
  const suit = cards[0].suit;
  if (!SUITS.includes(suit)) return false;
  if (cards.some((c) => c.suit !== suit)) return false;

  const ranksLow = cards.map((c) => c.rank).sort((a, b) => a - b);
  if (isConsecutiveList(ranksLow)) return true;

  // Ace high (Q-K-A, 10-J-Q-K-A). Note: K-A-2 has [2,13,14] -> NOT consecutive, rejected!
  if (ranksLow.includes(1)) {
    const ranksHigh = cards.map((c) => (c.rank === 1 ? 14 : c.rank)).sort((a, b) => a - b);
    if (isConsecutiveList(ranksHigh)) return true;
  }
  return false;
}

export function isImpureSequence(cards, wildRank) {
  if (!cards || cards.length < 3 || cards.length > 13) return false;
  if (isPureSequence(cards)) return false;

  const naturals = [];
  let jokerCount = 0;
  for (const c of cards) {
    if (isWildCard(c, wildRank)) {
      jokerCount++;
    } else {
      naturals.push(c);
    }
  }

  if (naturals.length === 0) {
    // 3+ wilds can form an impure sequence
    return true;
  }

  const suit = naturals[0].suit;
  if (!SUITS.includes(suit) || naturals.some((c) => c.suit !== suit)) return false;

  // No duplicate natural ranks in a single sequence
  const rankSet = new Set(naturals.map((c) => c.rank));
  if (rankSet.size !== naturals.length) return false;

  const totalLen = cards.length;

  // Try Ace low (1..13)
  const lowRanks = [...rankSet].sort((a, b) => a - b);
  const lowSpan = lowRanks[lowRanks.length - 1] - lowRanks[0] + 1;
  if (lowSpan <= totalLen) {
    const neededInside = lowSpan - naturals.length;
    if (jokerCount >= neededInside) return true;
  }

  // Try Ace high (2..14)
  if (rankSet.has(1)) {
    const highRanks = naturals.map((c) => (c.rank === 1 ? 14 : c.rank)).sort((a, b) => a - b);
    const highSpan = highRanks[highRanks.length - 1] - highRanks[0] + 1;
    if (highSpan <= totalLen) {
      const neededInside = highSpan - naturals.length;
      if (jokerCount >= neededInside) return true;
    }
  }

  return false;
}

export function isSet(cards, wildRank) {
  if (!cards || cards.length < 3 || cards.length > 4) return false;
  const naturals = [];
  for (const c of cards) {
    if (!isWildCard(c, wildRank)) {
      naturals.push(c);
    }
  }
  if (naturals.length === 0) return true;

  const targetRank = naturals[0].rank;
  if (naturals.some((c) => c.rank !== targetRank)) return false;

  // All natural suits in a set must be distinct
  const suitSet = new Set(naturals.map((c) => c.suit));
  return suitSet.size === naturals.length;
}

export function classifyGroup(cards, wildRank) {
  if (!cards || cards.length === 0) return 'INVALID';
  if (isPureSequence(cards)) return 'PURE_SEQUENCE';
  if (isImpureSequence(cards, wildRank)) return 'IMPURE_SEQUENCE';
  if (isSet(cards, wildRank)) return 'SET';
  return 'INVALID';
}

export function validateDeclaration(groups, wildRank, expectedHand = null) {
  const flat = groups.flat();
  if (flat.length !== 13) {
    return {
      isValid: false,
      pureSeqCount: 0,
      impureSeqCount: 0,
      setCount: 0,
      groupResults: groups.map((g) => ({ type: classifyGroup(g, wildRank), size: g.length })),
      reasonEn: `Declaration must contain exactly 13 remaining cards (found ${flat.length}).`,
      reasonHi: `डिक्लेरेशन में ठीक 13 कार्ड होने चाहिए (अभी ${flat.length} हैं)।`,
    };
  }

  const seenIds = new Set();
  for (const c of flat) {
    if (!c || !c.id || seenIds.has(c.id)) {
      return {
        isValid: false,
        pureSeqCount: 0,
        impureSeqCount: 0,
        setCount: 0,
        groupResults: [],
        reasonEn: 'Duplicate or invalid physical card detected in declaration.',
        reasonHi: 'डिक्लेरेशन में डुप्लिकेट या अमान्य कार्ड पाया गया।',
      };
    }
    seenIds.add(c.id);
  }

  if (expectedHand) {
    const expectedIds = new Set(expectedHand.map((c) => c.id));
    if (expectedIds.size !== 13 || [...seenIds].some((id) => !expectedIds.has(id))) {
      return {
        isValid: false,
        pureSeqCount: 0,
        impureSeqCount: 0,
        setCount: 0,
        groupResults: [],
        reasonEn: 'Submitted cards do not match your actual hand.',
        reasonHi: 'भेजे गए कार्ड आपके वास्तविक हाथ से मेल नहीं खाते।',
      };
    }
  }

  let pureSeqCount = 0;
  let impureSeqCount = 0;
  let setCount = 0;
  let invalidCount = 0;

  const groupResults = groups.map((g) => {
    const type = classifyGroup(g, wildRank);
    if (type === 'PURE_SEQUENCE') pureSeqCount++;
    else if (type === 'IMPURE_SEQUENCE') impureSeqCount++;
    else if (type === 'SET') setCount++;
    else invalidCount++;
    return {
      type,
      cardIds: g.map((c) => c.id),
      cards: g,
    };
  });

  if (invalidCount > 0) {
    return {
      isValid: false,
      pureSeqCount,
      impureSeqCount,
      setCount,
      groupResults,
      reasonEn: `${invalidCount} group(s) are not valid sequences or sets (note: K-A-2 and duplicate suits in a set are invalid).`,
      reasonHi: `${invalidCount} समूह वैध सीक्वेंस या सेट नहीं हैं (ध्यान दें: K-A-2 और सेट में एक ही सूट के दो कार्ड अमान्य हैं)।`,
    };
  }

  if (pureSeqCount < 1) {
    return {
      isValid: false,
      pureSeqCount,
      impureSeqCount,
      setCount,
      groupResults,
      reasonEn: 'At least 1 Pure Sequence (no jokers) is required.',
      reasonHi: 'कम से कम 1 प्योर सीक्वेंस (बिना जोकर) आवश्यक है।',
    };
  }

  if (pureSeqCount + impureSeqCount < 2) {
    return {
      isValid: false,
      pureSeqCount,
      impureSeqCount,
      setCount,
      groupResults,
      reasonEn: 'At least 2 Sequences (including 1 Pure Sequence) are required.',
      reasonHi: 'कम से कम 2 सीक्वेंस (1 प्योर सीक्वेंस सहित) आवश्यक हैं।',
    };
  }

  return {
    isValid: true,
    pureSeqCount,
    impureSeqCount,
    setCount,
    groupResults,
    reasonEn: 'Valid declaration! Pure Sequence, Second Sequence, and all remaining melds verified.',
    reasonHi: 'वैध डिक्लेरेशन! प्योर सीक्वेंस, दूसरा सीक्वेंस और सभी समूह सही हैं।',
  };
}

/**
 * Optimal deadwood calculator for 13-card Indian Rummy.
 * Evaluates minimum unmatched points (capped at 80) respecting 1st-life and 2nd-life requirements.
 */
export function calculateDeadwoodPoints(hand, wildRank) {
  if (!hand || hand.length === 0) return 0;
  const n = hand.length;
  const cardPts = hand.map((c) => cardPointValue(c, wildRank));
  const totalPts = Math.min(80, cardPts.reduce((a, b) => a + b, 0));

  // Generate candidate valid melds as bitmasks
  const pureMasks = [];
  const impureMasks = [];
  const setMasks = [];

  // Enumerate subsets of size 3..5 for sequences and 3..4 for sets
  // For speed & completeness on 13 cards:
  // 1) Pure sequences: group non-jokers by suit, sort by rank, find runs of length >= 3
  const bySuit = { S: [], H: [], C: [], D: [] };
  const wildIndices = [];
  const nonWildIndices = [];

  for (let i = 0; i < n; i++) {
    const c = hand[i];
    if (isWildCard(c, wildRank)) wildIndices.push(i);
    else nonWildIndices.push(i);
    if (!c.isPrintedJoker && SUITS.includes(c.suit)) {
      bySuit[c.suit].push(i);
    }
  }

  // Generate subsets of size >= 3 within each suit for pure sequences
  for (const suit of SUITS) {
    const idxs = bySuit[suit];
    if (idxs.length < 3) continue;
    const m = idxs.length;
    for (let mask = 0; mask < (1 << m); mask++) {
      const bits = countBits(mask);
      if (bits < 3 || bits > 7) continue;
      const subCards = [];
      let fullMask = 0;
      for (let b = 0; b < m; b++) {
        if (mask & (1 << b)) {
          subCards.push(hand[idxs[b]]);
          fullMask |= (1 << idxs[b]);
        }
      }
      if (isPureSequence(subCards)) {
        pureMasks.push(fullMask);
      }
    }
  }

  if (pureMasks.length === 0) {
    return totalPts;
  }

  // Generate impure sequences (same-suit non-wild subsets + 1..wildIndices.length jokers)
  const nonWildBySuit = { S: [], H: [], C: [], D: [] };
  const nonWildByRank = new Map();
  for (const i of nonWildIndices) {
    const c = hand[i];
    nonWildBySuit[c.suit].push(i);
    if (!nonWildByRank.has(c.rank)) nonWildByRank.set(c.rank, []);
    nonWildByRank.get(c.rank).push(i);
  }

  // Wild subsets (up to all wilds)
  const wildMasks = [0];
  for (let wm = 1; wm < (1 << wildIndices.length); wm++) {
    let fullW = 0;
    for (let b = 0; b < wildIndices.length; b++) {
      if (wm & (1 << b)) fullW |= (1 << wildIndices[b]);
    }
    wildMasks.push(fullW);
  }

  for (const suit of SUITS) {
    const idxs = nonWildBySuit[suit];
    const m = idxs.length;
    for (let mask = 1; mask < (1 << m); mask++) {
      const bits = countBits(mask);
      if (bits > 6) continue;
      let natMask = 0;
      const natCards = [];
      for (let b = 0; b < m; b++) {
        if (mask & (1 << b)) {
          natMask |= (1 << idxs[b]);
          natCards.push(hand[idxs[b]]);
        }
      }
      for (const wMask of wildMasks) {
        if (wMask === 0) continue;
        const combined = natMask | wMask;
        const size = countBits(combined);
        if (size < 3 || size > 6) continue;
        const cards = maskToCards(hand, combined);
        if (isImpureSequence(cards, wildRank)) {
          impureMasks.push(combined);
        }
      }
    }
  }

  // Sets: same rank, distinct suits + optional wilds, size 3..4
  for (const [, idxs] of nonWildByRank.entries()) {
    const m = idxs.length;
    for (let mask = 1; mask < (1 << m); mask++) {
      const bits = countBits(mask);
      if (bits > 4) continue;
      let natMask = 0;
      for (let b = 0; b < m; b++) {
        if (mask & (1 << b)) natMask |= (1 << idxs[b]);
      }
      for (const wMask of wildMasks) {
        const combined = natMask | wMask;
        const size = countBits(combined);
        if (size < 3 || size > 4) continue;
        const cards = maskToCards(hand, combined);
        if (isSet(cards, wildRank)) {
          setMasks.push(combined);
        }
      }
    }
  }

  // 1) Pure sequence only (no 2nd sequence) best score
  let bestPoints = totalPts;
  for (const pMask of pureMasks) {
    const pts = sumUnmaskedPoints(cardPts, n, pMask);
    if (pts < bestPoints) bestPoints = pts;
  }

  // 2) Pure sequence + 2nd sequence (pure or impure) + any additional disjoint melds
  const allSecondSeqs = [...pureMasks, ...impureMasks];
  const allAdditionalMelds = [...pureMasks, ...impureMasks, ...setMasks];

  // Memoize best points after choosing 1 pure + 1 second sequence
  const memo = new Map();
  function solveAdditional(usedMask) {
    if (memo.has(usedMask)) return memo.get(usedMask);
    let minPts = sumUnmaskedPoints(cardPts, n, usedMask);
    for (const mMask of allAdditionalMelds) {
      if ((usedMask & mMask) === 0) {
        const sub = solveAdditional(usedMask | mMask);
        if (sub < minPts) minPts = sub;
        if (minPts === 0) break;
      }
    }
    memo.set(usedMask, minPts);
    return minPts;
  }

  for (const pMask of pureMasks) {
    for (const sMask of allSecondSeqs) {
      if ((pMask & sMask) === 0) {
        const pts = solveAdditional(pMask | sMask);
        if (pts < bestPoints) bestPoints = pts;
        if (bestPoints === 0) return 0;
      }
    }
  }

  return Math.min(80, bestPoints);
}

function countBits(mask) {
  let c = 0;
  let m = mask;
  while (m) {
    m &= m - 1;
    c++;
  }
  return c;
}

function maskToCards(hand, mask) {
  const res = [];
  for (let i = 0; i < hand.length; i++) {
    if (mask & (1 << i)) res.push(hand[i]);
  }
  return res;
}

function sumUnmaskedPoints(cardPts, n, usedMask) {
  let sum = 0;
  for (let i = 0; i < n; i++) {
    if ((usedMask & (1 << i)) === 0) sum += cardPts[i];
  }
  return sum;
}

export function sortHandBySuitAndRank(cards, wildRank) {
  const byBucket = { S: [], H: [], C: [], D: [], PJ: [] };

  for (const c of cards) {
    if (c.isPrintedJoker || c.rank === 0) {
      byBucket.PJ.push(c);
    } else {
      byBucket[c.suit].push(c);
    }
  }

  const groups = [];
  for (const key of ['S', 'H', 'C', 'D']) {
    if (byBucket[key].length > 0) {
      byBucket[key].sort((a, b) => a.rank - b.rank || a.deck - b.deck);
      groups.push(byBucket[key]);
    }
  }
  if (byBucket.PJ.length > 0) {
    byBucket.PJ.sort((a, b) => a.deck - b.deck);
    groups.push(byBucket.PJ);
  }
  return groups;
}

export function computeCommissionAndNet(grossReward) {
  const gross = Math.max(0, Math.floor(grossReward));
  const commission = Math.floor(gross * 0.10);
  const net = gross - commission;
  return { grossReward: gross, commission, netReward: net };
}

export function getDropPenalty(mode, hasDrawnInHand) {
  if (mode === GAME_MODES.DEALS_3 || mode === GAME_MODES.DEALS_6) {
    // In Deals Rummy, dropping is disallowed or costs 20/40; standard rule: disallow drop or allow 20/40.
    // Let's check: prompt says "Where the selected mode allows Drop, show the penalty before confirmation."
    // Points, Pool 101, Pool 201 allow Drop cleanly! Deals Rummy usually disallows Drop in standard Indian Rummy.
    return { allowed: false, points: 0, reasonEn: 'Drop is not allowed in Deals Rummy.', reasonHi: 'डील्स रमी में ड्रॉप की अनुमति नहीं है।' };
  }
  if (mode === GAME_MODES.POOL_201) {
    const pts = hasDrawnInHand ? 50 : 25;
    return { allowed: true, points: pts, reasonEn: hasDrawnInHand ? 'Middle Drop: 50 pts' : 'First Drop: 25 pts', reasonHi: hasDrawnInHand ? 'मिडिल ड्रॉप: 50 अंक' : 'फर्स्ट ड्रॉप: 25 अंक' };
  }
  // Points & Pool 101
  const pts = hasDrawnInHand ? 40 : 20;
  return { allowed: true, points: pts, reasonEn: hasDrawnInHand ? 'Middle Drop: 40 pts' : 'First Drop: 20 pts', reasonHi: hasDrawnInHand ? 'मिडिल ड्रॉप: 40 अंक' : 'फर्स्ट ड्रॉप: 20 अंक' };
}
