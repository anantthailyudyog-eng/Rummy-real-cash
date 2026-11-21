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
        cards.push({ id: `d${deck}_${suit}_${rank}`, deck, suit, rank, isPrintedJoker: false });
      }
    }
    cards.push({ id: `d${deck}_PJ_0`, deck, suit: 'JOKER', rank: 0, isPrintedJoker: true });
  }
  return cards;
}

export function secureShuffle(cards) {
  const arr = [...cards];
  const randBuf = new Uint32Array(1);
  for (let i = arr.length - 1; i > 0; i--) {
    crypto.getRandomValues(randBuf);
    const j = randBuf[0] % (i + 1);
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

export function computeWildRank(jokerIndicator) {
  if (!jokerIndicator || jokerIndicator.isPrintedJoker || jokerIndicator.rank === 0) return 1;
  return jokerIndicator.rank;
}

export function isWildCard(card, wildRank) {
  if (!card) return false;
  return Boolean(card.isPrintedJoker || card.rank === 0 || card.rank === wildRank);
}

export function cardPointValue(card, wildRank) {
  if (!card || isWildCard(card, wildRank)) return 0;
  return card.rank === 1 || card.rank >= 10 ? 10 : card.rank;
}

export function dealHandFromDeck(shuffledDeck, playerCount) {
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
  return { hands, jokerIndicator, wildRank, openPile: [firstOpenCard], closedStock };
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
  if (!SUITS.includes(suit) || cards.some((c) => c.suit !== suit)) return false;
  const ranksLow = cards.map((c) => c.rank).sort((a, b) => a - b);
  if (isConsecutiveList(ranksLow)) return true;
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
    if (isWildCard(c, wildRank)) jokerCount++;
    else naturals.push(c);
  }
  if (naturals.length === 0) return true;
  const suit = naturals[0].suit;
  if (!SUITS.includes(suit) || naturals.some((c) => c.suit !== suit)) return false;
  const rankSet = new Set(naturals.map((c) => c.rank));
  if (rankSet.size !== naturals.length) return false;

  const totalLen = cards.length;
  const lowRanks = [...rankSet].sort((a, b) => a - b);
  if (lowRanks[lowRanks.length - 1] - lowRanks[0] + 1 <= totalLen) {
    if (jokerCount >= lowRanks[lowRanks.length - 1] - lowRanks[0] + 1 - naturals.length) return true;
  }
  if (rankSet.has(1)) {
    const highRanks = naturals.map((c) => (c.rank === 1 ? 14 : c.rank)).sort((a, b) => a - b);
    if (highRanks[highRanks.length - 1] - highRanks[0] + 1 <= totalLen) {
      if (jokerCount >= highRanks[highRanks.length - 1] - highRanks[0] + 1 - naturals.length) return true;
    }
  }
  return false;
}

export function isSet(cards, wildRank) {
  if (!cards || cards.length < 3 || cards.length > 4) return false;
  const naturals = cards.filter((c) => !isWildCard(c, wildRank));
  if (naturals.length === 0) return true;
  const targetRank = naturals[0].rank;
  if (naturals.some((c) => c.rank !== targetRank)) return false;
  return new Set(naturals.map((c) => c.suit)).size === naturals.length;
}

export function classifyGroup(cards, wildRank) {
  if (!cards || cards.length === 0) return 'INVALID';
  if (isPureSequence(cards)) return 'PURE_SEQUENCE';
  if (isImpureSequence(cards, wildRank)) return 'IMPURE_SEQUENCE';
  if (isSet(cards, wildRank)) return 'SET';
  return 'INVALID';
}

export function validateDeclaration(groups, wildRank) {
  const flat = groups.flat();
  if (flat.length !== 13) {
    return {
      isValid: false,
      groupResults: groups.map((g) => ({ type: classifyGroup(g, wildRank), cards: g })),
      reasonEn: `Declaration must contain 13 remaining cards (found ${flat.length}).`,
      reasonHi: `डिक्लेरेशन में 13 कार्ड होने चाहिए (अभी ${flat.length} हैं)।`,
    };
  }
  let pureSeqCount = 0;
  let impureSeqCount = 0;
  let invalidCount = 0;
  const groupResults = groups.map((g) => {
    const type = classifyGroup(g, wildRank);
    if (type === 'PURE_SEQUENCE') pureSeqCount++;
    else if (type === 'IMPURE_SEQUENCE') impureSeqCount++;
    else if (type === 'INVALID') invalidCount++;
    return { type, cards: g };
  });

  if (invalidCount > 0) {
    return {
      isValid: false,
      groupResults,
      reasonEn: `${invalidCount} group(s) are invalid (K-A-2 and duplicate suits in sets are invalid).`,
      reasonHi: `${invalidCount} समूह अमान्य हैं (K-A-2 और सेट में समान सूट अमान्य हैं)।`,
    };
  }
  if (pureSeqCount < 1) {
    return {
      isValid: false,
      groupResults,
      reasonEn: 'At least 1 Pure Sequence (no jokers) is required.',
      reasonHi: 'कम से कम 1 प्योर सीक्वेंस आवश्यक है।',
    };
  }
  if (pureSeqCount + impureSeqCount < 2) {
    return {
      isValid: false,
      groupResults,
      reasonEn: 'At least 2 Sequences (including 1 Pure Sequence) are required.',
      reasonHi: 'कम से कम 2 सीक्वेंस (1 प्योर सहित) आवश्यक हैं।',
    };
  }
  return {
    isValid: true,
    groupResults,
    reasonEn: 'Valid declaration! All sequences and sets verified.',
    reasonHi: 'वैध डिक्लेरेशन! सभी सीक्वेंस और सेट सही हैं।',
  };
}

export function sortHandBySuitAndRank(cards) {
  const byBucket = { S: [], H: [], C: [], D: [], PJ: [] };
  for (const c of cards) {
    if (c.isPrintedJoker || c.rank === 0) byBucket.PJ.push(c);
    else byBucket[c.suit].push(c);
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
  const commission = Math.floor(gross * 0.1);
  return { grossReward: gross, commission, netReward: gross - commission };
}

export function getDropPenalty(mode, hasDrawnInHand) {
  if (mode === GAME_MODES.DEALS_3 || mode === GAME_MODES.DEALS_6) {
    return { allowed: false, points: 0, reasonEn: 'Drop is not allowed in Deals Rummy.', reasonHi: 'डील्स रमी में ड्रॉप की अनुमति नहीं है।' };
  }
  if (mode === GAME_MODES.POOL_201) {
    const pts = hasDrawnInHand ? 50 : 25;
    return { allowed: true, points: pts, reasonEn: hasDrawnInHand ? 'Middle Drop: 50 pts' : 'First Drop: 25 pts', reasonHi: hasDrawnInHand ? 'मिडिल ड्रॉप: 50 अंक' : 'फर्स्ट ड्रॉप: 25 अंक' };
  }
  const pts = hasDrawnInHand ? 40 : 20;
  return { allowed: true, points: pts, reasonEn: hasDrawnInHand ? 'Middle Drop: 40 pts' : 'First Drop: 20 pts', reasonHi: hasDrawnInHand ? 'मिडिल ड्रॉप: 40 अंक' : 'फर्स्ट ड्रॉप: 20 अंक' };
}

export function calculateSimpleDeadwood(hand, wildRank) {
  const groups = sortHandBySuitAndRank(hand);
  // Check natural pure runs within sorted suits
  let pureCount = 0;
  let seqCount = 0;
  const matchedIds = new Set();
  for (const g of groups) {
    if (g.length >= 3 && isPureSequence(g)) {
      pureCount++;
      seqCount++;
      g.forEach((c) => matchedIds.add(c.id));
    }
  }
  let pts = 0;
  for (const c of hand) {
    if (pureCount >= 1 && matchedIds.has(c.id)) continue;
    pts += cardPointValue(c, wildRank);
  }
  return Math.min(80, pts);
}
