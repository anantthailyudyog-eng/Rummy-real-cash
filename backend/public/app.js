import {
  GAME_MODES,
  createDeck106,
  secureShuffle,
  dealHandFromDeck,
  classifyGroup,
  validateDeclaration,
  sortHandBySuitAndRank,
  computeCommissionAndNet,
  getDropPenalty,
  isWildCard,
  calculateSimpleDeadwood,
} from '/rummyClientEngine.js';
import { RummyAudio } from '/audio.js';

const audio = new RummyAudio();

const STRINGS = {
  en: {
    brand: 'Velvet Rummy Den',
    chips: 'Practice Chips',
    dailyBonus: 'Daily Bonus +1,000',
    bonusClaimed: 'Bonus Claimed',
    soundOn: 'Sound On',
    soundOff: 'Muted',
    voiceOn: 'Voice ID',
    voiceOff: 'Voice Off',
    chooseMode: 'Select Format',
    chooseSeats: 'Table Capacity',
    playNow: 'Play Now',
    cpuPractice: 'CPU Practice (Offline)',
    points: 'Points Rummy',
    pointsSub: 'Fast single deal · 100 stake',
    pool101: 'Pool 101',
    pool101Sub: 'Elimination at 101 pts',
    pool201: 'Pool 201',
    pool201Sub: 'Elimination at 201 pts',
    deals3: 'Deals (3 Deals)',
    deals3Sub: '3-deal series · Best score',
    deals6: 'Deals (6 Deals)',
    deals6Sub: '6-deal series · Best score',
    backLobby: 'Lobby',
    searchingPlayers: 'Seated! Searching for real players...',
    waitingLone: 'Waiting for at least 1 more player to join...',
    dealIn: 'Dealing in',
    yourTurnDraw: 'Your Turn: Draw from Stock or Open Pile',
    yourTurnDiscard: 'Your Turn: Discard 1 card or Finish',
    opponentTurn: 'Turn:',
    jokerLabel: 'Wild Joker',
    stockLabel: 'Closed Stock',
    openLabel: 'Open Pile',
    finishLabel: 'FINISH',
    sort: 'Sort',
    newGroup: 'New Group',
    moveHere: 'Move Here',
    ungroup: 'Ungroup',
    discard: 'Discard',
    drop: 'Drop',
    reviewTitle: 'Finish Review',
    reviewNote: 'Confirm your 13 grouped cards and finish discard:',
    cancel: 'Cancel',
    confirmDeclare: 'Confirm Declaration',
    congrats: 'Congratulations!',
    handOver: 'Hand Complete',
    grossReward: 'Gross Win',
    commission: '10% Commission',
    netReward: 'Net Reward',
    playAgain: 'Play Again',
    nextDeal: 'Next Deal',
    dropConfirmTitle: 'Confirm Drop',
    dropConfirmBtn: 'Confirm Drop',
  },
  hi: {
    brand: 'वेलवेट रमी डेन',
    chips: 'प्रैक्टिस चिप्स',
    dailyBonus: 'दैनिक बोनस +1,000',
    bonusClaimed: 'बोनस मिला',
    soundOn: 'ध्वनि चालू',
    soundOff: 'म्यूट',
    voiceOn: 'वॉइस आईडी',
    voiceOff: 'वॉइस बंद',
    chooseMode: 'फॉर्मेट चुनें',
    chooseSeats: 'टेबल सीटें',
    playNow: 'अभी खेलें (Play Now)',
    cpuPractice: 'CPU प्रैक्टिस (ऑफ़लाइन)',
    points: 'पॉइंट्स रमी',
    pointsSub: 'तेज़ सिंगल डील · 100 दांव',
    pool101: 'पूल 101',
    pool101Sub: '101 अंक पर एलिमिनेशन',
    pool201: 'पूल 201',
    pool201Sub: '201 अंक पर एलिमिनेशन',
    deals3: 'डील्स (3 डील)',
    deals3Sub: '3-डील सीरीज़',
    deals6: 'डील्स (6 डील)',
    deals6Sub: '6-डील सीरीज़',
    backLobby: 'लॉबी',
    searchingPlayers: 'सीट पक्की! असली खिलाड़ियों की खोज...',
    waitingLone: 'कम से कम 1 और खिलाड़ी का इंतज़ार...',
    dealIn: 'डील शुरू होने में',
    yourTurnDraw: 'आपकी बारी: स्टॉक या ओपन पाइल से कार्ड उठाएं',
    yourTurnDiscard: 'आपकी बारी: 1 कार्ड फेंके या फिनिश करें',
    opponentTurn: 'बारी:',
    jokerLabel: 'वाइल्ड जोकर',
    stockLabel: 'बंद स्टॉक',
    openLabel: 'ओपन पाइल',
    finishLabel: 'फिनिश',
    sort: 'सॉर्ट',
    newGroup: 'नया ग्रुप',
    moveHere: 'यहाँ रखें',
    ungroup: 'अनग्रुप',
    discard: 'डिस्कार्ड',
    drop: 'ड्रॉप',
    reviewTitle: 'फिनिश समीक्षा',
    reviewNote: 'अपने 13 समूहित कार्ड और अंतिम कार्ड की जांच करें:',
    cancel: 'रद्द करें',
    confirmDeclare: 'डिक्लेयर पक्का करें',
    congrats: 'बधाई हो!',
    handOver: 'डील समाप्त',
    grossReward: 'कुल जीत',
    commission: '10% कमीशन',
    netReward: 'शुद्ध इनाम',
    playAgain: 'फिर से खेलें',
    nextDeal: 'अगली डील',
    dropConfirmTitle: 'ड्रॉप की पुष्टि करें',
    dropConfirmBtn: 'ड्रॉप पक्का करें',
  },
};

function loadOrCreateGuest() {
  let id = localStorage.getItem('vrd_player_id');
  if (!id) {
    id = 'guest_' + Math.random().toString(36).slice(2, 10);
    localStorage.setItem('vrd_player_id', id);
  }
  let name = localStorage.getItem('vrd_player_name');
  if (!name) {
    name = `Velvet_${id.slice(-4).toUpperCase()}`;
    localStorage.setItem('vrd_player_name', name);
  }
  let avatarId = Number(localStorage.getItem('vrd_avatar_id'));
  if (!Number.isInteger(avatarId) || localStorage.getItem('vrd_avatar_id') === null) {
    avatarId = Math.floor(Math.random() * 6);
    localStorage.setItem('vrd_avatar_id', String(avatarId));
  }
  let chips = Number(localStorage.getItem('vrd_chips'));
  if (!Number.isFinite(chips) || localStorage.getItem('vrd_chips') === null) {
    chips = 5000;
    localStorage.setItem('vrd_chips', String(chips));
  }
  return {
    playerId: id,
    reconnectToken: localStorage.getItem('vrd_reconnect_token') || '',
    name,
    avatarId,
    chips,
    lastBonusDate: localStorage.getItem('vrd_bonus_date') || '',
  };
}

const AVATAR_COLORS = [
  ['#f59e0b', '#78350f'],
  ['#10b981', '#064e3b'],
  ['#38bdf8', '#0c4a6e'],
  ['#f43f5e', '#881337'],
  ['#a855f7', '#4c1d95'],
  ['#eab308', '#713f12'],
];

function renderAvatarSvg(avatarId, name) {
  const [c1, c2] = AVATAR_COLORS[Math.abs(avatarId) % AVATAR_COLORS.length];
  const initial = (name || 'V').slice(0, 1).toUpperCase();
  return `<svg viewBox="0 0 64 64" width="100%" height="100%">
    <defs>
      <radialGradient id="av_${avatarId}" cx="35%" cy="30%" r="70%">
        <stop offset="0%" stop-color="${c1}" />
        <stop offset="100%" stop-color="${c2}" />
      </radialGradient>
    </defs>
    <circle cx="32" cy="32" r="32" fill="url(#av_${avatarId})" />
    <circle cx="32" cy="24" r="10" fill="rgba(255,255,255,0.88)" />
    <path d="M14 54 C14 40 50 40 50 54" fill="rgba(255,255,255,0.82)" />
    <text x="32" y="28" text-anchor="middle" font-size="11" font-weight="800" fill="${c2}">${initial}</text>
  </svg>`;
}

function rankLabel(rank) {
  if (rank === 0) return '★';
  if (rank === 1) return 'A';
  if (rank === 11) return 'J';
  if (rank === 12) return 'Q';
  if (rank === 13) return 'K';
  return String(rank);
}

function suitSymbol(suit) {
  return { S: '♠', H: '♥', C: '♣', D: '♦', JOKER: '🃏' }[suit] || '';
}

function renderCardHtml(card, { selected = false, wildRank = 1, isNew = false, dealAnim = false, draggable = true } = {}) {
  const isRed = card.suit === 'H' || card.suit === 'D';
  const wild = isWildCard(card, wildRank);
  const cls = [
    'playing-card',
    isRed ? 'red' : '',
    card.isPrintedJoker ? 'joker-printed' : '',
    selected ? 'selected' : '',
    isNew ? 'newly-drawn' : '',
    dealAnim ? 'deal-fly' : '',
  ]
    .filter(Boolean)
    .join(' ');
  const rText = rankLabel(card.rank);
  const sText = suitSymbol(card.suit);
  return `<div class="${cls}" data-card-id="${card.id}" ${draggable ? 'draggable="true"' : ''}>
    ${isNew ? '<span class="new-pip">NEW</span>' : ''}
    <div class="card-corner"><span>${rText}</span><span>${sText}</span></div>
    <div class="card-suit-center">${sText}</div>
    ${wild ? '<span class="wild-pip">WILD</span>' : ''}
  </div>`;
}

const state = {
  screen: 'LOBBY', // LOBBY | TABLE
  lang: localStorage.getItem('vrd_lang') || 'en',
  profile: loadOrCreateGuest(),
  selectedMode: GAME_MODES.POINTS,
  selectedSeats: 4,
  isOfflineCpu: false,
  connectionStatus: 'IDLE', // IDLE | CONNECTING | CONNECTED | RECONNECTING | OFFLINE
  tableState: null,
  ws: null,
  localGroups: [], // string[][] of cardIds
  selectedCardIds: [],
  lastDealSequenceId: 0,
  shouldAnimateDeal: false,
  finishReview: null, // { finishCardId, finishCard, groupsCards, restoreGroupIdx, restoreCardIdx, validation }
  dropConfirm: null,
  toast: null,
  dragCardId: null,
  cpuEngineState: null,
  settlementCreditedKey: null,
};

let countdownInterval = null;
let toastTimer = null;

function showToast(msg) {
  state.toast = msg;
  render();
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    state.toast = null;
    render();
  }, 3000);
}

function t(key) {
  return (STRINGS[state.lang] && STRINGS[state.lang][key]) || STRINGS.en[key] || key;
}

function arrangementKey(tableId, dealNumber) {
  return `vrd_arr_${tableId}_${dealNumber}_${state.profile.playerId}`;
}

function syncLocalGroupsWithServerHand(serverHand, serverArrangedGroups, tableId, dealNumber) {
  const handMap = new Map(serverHand.map((c) => [c.id, c]));
  const savedRaw = localStorage.getItem(arrangementKey(tableId, dealNumber));
  let baseGroups = null;
  if (savedRaw) {
    try {
      baseGroups = JSON.parse(savedRaw);
    } catch {}
  }
  if (!baseGroups && Array.isArray(serverArrangedGroups) && serverArrangedGroups.length > 0) {
    baseGroups = serverArrangedGroups;
  }

  if (state.localGroups.length > 0 && sameCardSet(state.localGroups.flat(), serverHand)) {
    // Keep exact current manual arrangement!
    return;
  }

  if (baseGroups) {
    // Reconcile saved arrangement with current hand (handles draw/discard on reconnect)
    const cleaned = baseGroups
      .map((g) => g.filter((id) => handMap.has(id)))
      .filter((g) => g.length > 0);
    const present = new Set(cleaned.flat());
    const added = serverHand.filter((c) => !present.has(c.id)).map((c) => c.id);
    if (added.length > 0) cleaned.push(added);
    state.localGroups = cleaned;
  } else {
    state.localGroups = serverHand.length > 0 ? [serverHand.map((c) => c.id)] : [];
  }
  persistArrangement();
}

function sameCardSet(idsA, cardsB) {
  if (idsA.length !== cardsB.length) return false;
  const setA = new Set(idsA);
  if (setA.size !== idsA.length) return false;
  return cardsB.every((c) => setA.has(c.id));
}

function persistArrangement() {
  const ts = state.tableState;
  if (!ts || !ts.tableId) return;
  localStorage.setItem(arrangementKey(ts.tableId, ts.dealNumber), JSON.stringify(state.localGroups));
  if (!state.isOfflineCpu && state.ws && state.ws.readyState === WebSocket.OPEN) {
    state.ws.send(
      JSON.stringify({
        type: 'SAVE_ARRANGEMENT',
        tableId: ts.tableId,
        playerId: state.profile.playerId,
        groups: state.localGroups,
      })
    );
  }
}

function applyServerTableState(newState) {
  const prevSeatsCount = state.tableState?.seats?.length || 0;
  const prevStatus = state.tableState?.status;
  if (newState.dealSequenceId > state.lastDealSequenceId && newState.status === 'PLAYING') {
    state.lastDealSequenceId = newState.dealSequenceId;
    state.shouldAnimateDeal = true;
    state.localGroups = [];
    state.selectedCardIds = [];
    audio.playDeal();
  } else {
    state.shouldAnimateDeal = false;
  }

  if (newState.seats.length > prevSeatsCount) {
    audio.playSeat();
  }

  state.tableState = newState;

  if (newState.myHand && newState.myHand.length > 0) {
    syncLocalGroupsWithServerHand(newState.myHand, newState.myArrangedGroups, newState.tableId, newState.dealNumber);
  } else {
    state.localGroups = [];
  }

  // Sync guest chips on final result ONCE
  if ((newState.status === 'HAND_RESULT' || newState.status === 'MATCH_RESULT') && prevStatus === 'PLAYING') {
    const me = newState.seats.find((s) => s.playerId === state.profile.playerId);
    if (me) {
      state.profile.chips = me.chips;
      localStorage.setItem('vrd_chips', String(me.chips));
    }
    if (newState.result?.handWinnerId === state.profile.playerId || newState.result?.matchWinnerId === state.profile.playerId) {
      audio.playWinCheer();
    }
  }

  render();
}

async function joinMultiplayerTable() {
  audio.unlock();
  state.isOfflineCpu = false;
  state.screen = 'TABLE';
  state.connectionStatus = 'CONNECTING';
  state.lastDealSequenceId = 0;
  state.localGroups = [];
  state.selectedCardIds = [];

  // Immediate local seat preview while server responds in background
  state.tableState = {
    tableId: 'pending',
    mode: state.selectedMode,
    maxSeats: state.selectedSeats,
    stake: 100,
    dealNumber: 0,
    totalDeals: state.selectedMode === GAME_MODES.DEALS_3 ? 3 : state.selectedMode === GAME_MODES.DEALS_6 ? 6 : 1,
    dealSequenceId: 0,
    status: 'WAITING',
    countdownEndsAt: null,
    seats: [
      {
        playerId: state.profile.playerId,
        name: state.profile.name,
        avatarId: state.profile.avatarId,
        seatIndex: 0,
        chips: state.profile.chips,
        matchPoints: 0,
        cardCount: 0,
        connected: true,
      },
    ],
    mySeatIndex: 0,
    myHand: [],
  };
  audio.playSeat();
  render();

  try {
    const res = await fetch('/api/tables/join', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        playerId: state.profile.playerId,
        reconnectToken: state.profile.reconnectToken,
        name: state.profile.name,
        avatarId: state.profile.avatarId,
        chips: state.profile.chips,
        mode: state.selectedMode,
        maxSeats: state.selectedSeats,
        stake: 100,
      }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Join failed');
    state.profile.reconnectToken = data.reconnectToken;
    localStorage.setItem('vrd_reconnect_token', data.reconnectToken);
    applyServerTableState(data.state);
    connectWebSocket(data.state.tableId);
  } catch (err) {
    state.connectionStatus = 'OFFLINE';
    showToast(err.message);
    render();
  }
}

function connectWebSocket(tableId) {
  if (state.ws) {
    state.ws.close();
    state.ws = null;
  }
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const ws = new WebSocket(`${proto}//${location.host}/ws?playerId=${encodeURIComponent(state.profile.playerId)}&tableId=${encodeURIComponent(tableId)}`);
  state.ws = ws;
  ws.onopen = () => {
    state.connectionStatus = 'CONNECTED';
    ws.send(JSON.stringify({ type: 'SUBSCRIBE', playerId: state.profile.playerId, tableId }));
    render();
  };
  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data);
      if (msg.event === 'TABLE_STATE' && msg.state) {
        applyServerTableState(msg.state);
      } else if (msg.event === 'ACTION_OK' && msg.state) {
        applyServerTableState(msg.state);
      } else if (msg.event === 'ERROR') {
        audio.playError();
        showToast(msg.error);
      }
    } catch {}
  };
  ws.onclose = () => {
    if (state.screen === 'TABLE' && !state.isOfflineCpu) {
      state.connectionStatus = 'RECONNECTING';
      render();
      setTimeout(() => {
        if (state.screen === 'TABLE' && !state.isOfflineCpu && state.tableState?.tableId) {
          connectWebSocket(state.tableState.tableId);
        }
      }, 2000);
    }
  };
}

async function sendGameAction(payload) {
  if (state.isOfflineCpu) {
    handleOfflineCpuAction(payload);
    return;
  }
  try {
    const res = await fetch(`/api/tables/${state.tableState.tableId}/action`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...payload, playerId: state.profile.playerId }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Action failed');
    applyServerTableState(data.state);
  } catch (err) {
    audio.playError();
    showToast(err.message);
  }
}

// Offline CPU Practice mode
function startCpuPractice() {
  audio.unlock();
  state.isOfflineCpu = true;
  state.screen = 'TABLE';
  state.connectionStatus = 'OFFLINE';
  state.lastDealSequenceId = 0;
  state.localGroups = [];
  state.selectedCardIds = [];
  state.settlementCreditedKey = null;

  const cpuNames = ['Aarav', 'Meera', 'Kabir', 'Diya', 'Rohan'];
  const seats = [
    {
      playerId: state.profile.playerId,
      name: state.profile.name,
      avatarId: state.profile.avatarId,
      seatIndex: 0,
      chips: state.profile.chips,
      matchPoints: 0,
      lastHandPoints: 0,
      isDropped: false,
      isEliminated: false,
      hasDrawnThisHand: false,
      connected: true,
      hand: [],
    },
  ];
  for (let i = 1; i < state.selectedSeats; i++) {
    seats.push({
      playerId: `cpu_${i}`,
      name: `${cpuNames[i - 1]} (CPU)`,
      avatarId: i,
      seatIndex: i,
      chips: 5000,
      matchPoints: 0,
      lastHandPoints: 0,
      isDropped: false,
      isEliminated: false,
      hasDrawnThisHand: false,
      connected: true,
      hand: [],
    });
  }

  state.cpuEngineState = {
    tableId: 'cpu_local',
    mode: state.selectedMode,
    maxSeats: state.selectedSeats,
    stake: 100,
    dealNumber: 0,
    totalDeals: state.selectedMode === GAME_MODES.DEALS_3 ? 3 : state.selectedMode === GAME_MODES.DEALS_6 ? 6 : 1,
    dealSequenceId: 0,
    seats,
    activeTurnSeatIndex: 0,
    turnPhase: 'DRAW',
    drawnFromOpenCardId: null,
    jokerIndicator: null,
    wildRank: 1,
    openPile: [],
    closedStock: [],
    result: null,
    matchEntryDeducted: false,
  };
  dealNextCpuHand();
}

function dealNextCpuHand() {
  const c = state.cpuEngineState;
  if (!c.matchEntryDeducted && c.mode !== GAME_MODES.POINTS) {
    c.seats.forEach((s) => {
      s.chips = Math.max(0, s.chips - c.stake);
    });
    c.matchEntryDeducted = true;
  }
  const activeSeats = c.seats.filter((s) => !s.isEliminated);
  c.dealNumber += 1;
  c.dealSequenceId += 1;
  c.status = 'PLAYING';
  c.drawnFromOpenCardId = null;
  c.turnPhase = 'DRAW';
  c.result = null;
  const deck = secureShuffle(createDeck106());
  const dealt = dealHandFromDeck(deck, activeSeats.length);
  c.jokerIndicator = dealt.jokerIndicator;
  c.wildRank = dealt.wildRank;
  c.openPile = dealt.openPile;
  c.closedStock = dealt.closedStock;
  activeSeats.forEach((s, idx) => {
    s.hand = dealt.hands[idx];
    s.isDropped = false;
    s.hasDrawnThisHand = false;
    s.lastDrawnCardId = null;
    s.lastHandPoints = 0;
  });
  c.activeTurnSeatIndex = c.seats.findIndex((s) => !s.isEliminated);
  syncCpuToTableState();
}

function syncCpuToTableState() {
  const c = state.cpuEngineState;
  const me = c.seats[0];
  applyServerTableState({
    tableId: c.tableId,
    mode: c.mode,
    maxSeats: c.maxSeats,
    stake: c.stake,
    dealNumber: c.dealNumber,
    totalDeals: c.totalDeals,
    dealSequenceId: c.dealSequenceId,
    status: c.status,
    countdownEndsAt: null,
    activeTurnSeatIndex: c.activeTurnSeatIndex,
    turnPhase: c.turnPhase,
    drawnFromOpenCardId: c.activeTurnSeatIndex === 0 ? c.drawnFromOpenCardId : null,
    jokerIndicator: c.jokerIndicator,
    wildRank: c.wildRank,
    openPileTop: c.openPile[c.openPile.length - 1] || null,
    openPileCount: c.openPile.length,
    closedStockCount: c.closedStock.length,
    seats: c.seats.map((s) => ({
      ...s,
      cardCount: s.hand.length,
    })),
    mySeatIndex: 0,
    myHand: [...me.hand],
    myArrangedGroups: state.localGroups,
    myLastDrawnCardId: me.lastDrawnCardId,
    result: c.result,
  });
}

function handleOfflineCpuAction(payload) {
  const c = state.cpuEngineState;
  const me = c.seats[0];
  if (payload.type === 'NEXT_DEAL' || payload.type === 'PLAY_AGAIN') {
    if (c.status === 'MATCH_RESULT') {
      c.dealNumber = 0;
      c.matchEntryDeducted = false;
      c.seats.forEach((s) => {
        s.matchPoints = 0;
        s.isEliminated = false;
        s.isDropped = false;
      });
    }
    dealNextCpuHand();
    return;
  }
  if (c.status !== 'PLAYING' || c.activeTurnSeatIndex !== 0) return;

  if (payload.type === 'DRAW_CLOSED') {
    if (c.turnPhase !== 'DRAW') return;
    if (c.closedStock.length === 0 && c.openPile.length > 1) {
      const top = c.openPile.pop();
      c.closedStock = secureShuffle(c.openPile);
      c.openPile = [top];
    }
    const card = c.closedStock.pop();
    me.hand.push(card);
    me.lastDrawnCardId = card.id;
    me.hasDrawnThisHand = true;
    c.turnPhase = 'DISCARD';
    c.drawnFromOpenCardId = null;
    state.localGroups = [...state.localGroups, [card.id]];
    persistArrangement();
    audio.playDraw();
    syncCpuToTableState();
  } else if (payload.type === 'DRAW_OPEN') {
    if (c.turnPhase !== 'DRAW' || c.openPile.length === 0) return;
    const card = c.openPile.pop();
    me.hand.push(card);
    me.lastDrawnCardId = card.id;
    me.hasDrawnThisHand = true;
    c.turnPhase = 'DISCARD';
    c.drawnFromOpenCardId = card.id;
    state.localGroups = [...state.localGroups, [card.id]];
    persistArrangement();
    audio.playDraw();
    syncCpuToTableState();
  } else if (payload.type === 'DISCARD') {
    if (c.turnPhase !== 'DISCARD') return;
    if (payload.cardId === c.drawnFromOpenCardId) {
      audio.playError();
      showToast('Cannot immediately discard the card drawn from the open pile');
      return;
    }
    const idx = me.hand.findIndex((x) => x.id === payload.cardId);
    if (idx === -1) return;
    const [removed] = me.hand.splice(idx, 1);
    c.openPile.push(removed);
    state.localGroups = state.localGroups
      .map((g) => g.filter((id) => id !== payload.cardId))
      .filter((g) => g.length > 0);
    state.selectedCardIds = [];
    persistArrangement();
    audio.playDiscard();
    advanceCpuTurn();
  } else if (payload.type === 'DROP') {
    if (c.turnPhase !== 'DRAW') return;
    const dropInfo = getDropPenalty(c.mode, me.hasDrawnThisHand);
    if (!dropInfo.allowed) return;
    me.isDropped = true;
    me.lastHandPoints = dropInfo.points;
    const active = c.seats.filter((s) => !s.isDropped && !s.isEliminated);
    if (active.length === 1) {
      settleCpuHand(active[0], [active[0].hand]);
    } else {
      advanceCpuTurn();
    }
  } else if (payload.type === 'SUBMIT_FINISH') {
    const finishCard = me.hand.find((x) => x.id === payload.finishCardId);
    if (!finishCard) return;
    const remaining = me.hand.filter((x) => x.id !== payload.finishCardId);
    const byId = new Map(remaining.map((x) => [x.id, x]));
    const groupCards = payload.groups.map((g) => g.map((id) => byId.get(id)).filter(Boolean));
    const val = validateDeclaration(groupCards, c.wildRank);
    me.hand = remaining;
    c.openPile.push(finishCard);
    if (val.isValid) {
      settleCpuHand(me, groupCards);
    } else {
      me.wrongDeclaration = true;
      me.isDropped = true;
      me.lastHandPoints = 80;
      const active = c.seats.filter((s) => !s.isDropped && !s.isEliminated);
      if (active.length === 1) settleCpuHand(active[0], [active[0].hand]);
      else advanceCpuTurn();
    }
  }
}

function advanceCpuTurn() {
  const c = state.cpuEngineState;
  const total = c.seats.length;
  for (let step = 1; step <= total; step++) {
    const idx = (c.activeTurnSeatIndex + step) % total;
    if (!c.seats[idx].isDropped && !c.seats[idx].isEliminated) {
      c.activeTurnSeatIndex = idx;
      c.turnPhase = 'DRAW';
      c.drawnFromOpenCardId = null;
      break;
    }
  }
  syncCpuToTableState();
  if (c.activeTurnSeatIndex !== 0 && c.status === 'PLAYING') {
    setTimeout(() => runCpuTurn(c.activeTurnSeatIndex), 650);
  }
}

function runCpuTurn(seatIdx) {
  const c = state.cpuEngineState;
  if (!c || c.status !== 'PLAYING' || c.activeTurnSeatIndex !== seatIdx) return;
  const cpu = c.seats[seatIdx];
  if (c.closedStock.length === 0 && c.openPile.length > 1) {
    const top = c.openPile.pop();
    c.closedStock = secureShuffle(c.openPile);
    c.openPile = [top];
  }
  const topOpen = c.openPile[c.openPile.length - 1];
  const takeOpen = topOpen && isWildCard(topOpen, c.wildRank);
  const drawn = takeOpen ? c.openPile.pop() : c.closedStock.pop();
  if (drawn) {
    cpu.hand.push(drawn);
    cpu.hasDrawnThisHand = true;
  }
  // Discard highest-point non-wild card not equal to drawn open card
  const candidates = cpu.hand
    .filter((x) => !takeOpen || x.id !== drawn?.id)
    .sort((a, b) => (isWildCard(a, c.wildRank) ? -1 : a.rank) - (isWildCard(b, c.wildRank) ? -1 : b.rank));
  const discardCard = candidates[candidates.length - 1] || cpu.hand[0];
  cpu.hand = cpu.hand.filter((x) => x.id !== discardCard.id);
  c.openPile.push(discardCard);
  audio.playDiscard();
  advanceCpuTurn();
}

function settleCpuHand(winnerSeat, winningGroups) {
  const c = state.cpuEngineState;
  const scores = c.seats
    .filter((s) => !s.isEliminated)
    .map((s) => {
      const pts =
        s.playerId === winnerSeat.playerId
          ? 0
          : s.wrongDeclaration
          ? 80
          : s.isDropped
          ? s.lastHandPoints
          : calculateSimpleDeadwood(s.hand, c.wildRank);
      s.lastHandPoints = pts;
      s.matchPoints += pts;
      return {
        playerId: s.playerId,
        name: s.name,
        avatarId: s.avatarId,
        handPoints: pts,
        matchPoints: s.matchPoints,
        isWinner: s.playerId === winnerSeat.playerId,
        isDropped: s.isDropped,
        cards: s.hand,
      };
    });

  let matchFinished = c.mode === GAME_MODES.POINTS;
  let matchWinner = winnerSeat;
  let grossReward = 0;
  let commission = 0;
  let netReward = 0;

  if (c.mode === GAME_MODES.POINTS) {
    const lost = scores.filter((x) => !x.isWinner).reduce((acc, x) => acc + x.handPoints, 0);
    const settled = computeCommissionAndNet(lost);
    grossReward = settled.grossReward;
    commission = settled.commission;
    netReward = settled.netReward;
    winnerSeat.chips += netReward;
  } else if (c.mode === GAME_MODES.POOL_101 || c.mode === GAME_MODES.POOL_201) {
    const limit = c.mode === GAME_MODES.POOL_101 ? 101 : 201;
    c.seats.forEach((s) => {
      if (s.matchPoints >= limit) s.isEliminated = true;
    });
    const surv = c.seats.filter((s) => !s.isEliminated);
    if (surv.length <= 1) {
      matchFinished = true;
      matchWinner = surv[0] || [...c.seats].sort((a, b) => a.matchPoints - b.matchPoints)[0];
      const settled = computeCommissionAndNet(c.stake * c.seats.length);
      grossReward = settled.grossReward;
      commission = settled.commission;
      netReward = settled.netReward;
      matchWinner.chips += netReward;
    }
  } else if (c.dealNumber >= c.totalDeals) {
    matchFinished = true;
    matchWinner = [...c.seats].sort((a, b) => a.matchPoints - b.matchPoints)[0];
    const settled = computeCommissionAndNet(c.stake * c.seats.length);
    grossReward = settled.grossReward;
    commission = settled.commission;
    netReward = settled.netReward;
    matchWinner.chips += netReward;
  }

  c.status = matchFinished ? 'MATCH_RESULT' : 'HAND_RESULT';
  c.result = {
    handWinnerId: winnerSeat.playerId,
    handWinnerName: winnerSeat.name,
    matchWinnerId: matchFinished ? matchWinner.playerId : null,
    matchWinnerName: matchFinished ? matchWinner.name : null,
    matchFinished,
    dealNumber: c.dealNumber,
    totalDeals: c.totalDeals,
    winningGroups,
    handScores: scores,
    grossReward,
    commission,
    netReward,
  };
  state.profile.chips = c.seats[0].chips;
  localStorage.setItem('vrd_chips', String(c.seats[0].chips));
  syncCpuToTableState();
}

// Hand manipulation helpers
function moveSelectedTo(targetGroupIdx, targetPosInGroup) {
  if (state.selectedCardIds.length === 0) return;
  const selSet = new Set(state.selectedCardIds);
  const movingIds = [];
  const nextGroups = [];

  state.localGroups.forEach((g, gIdx) => {
    const kept = [];
    g.forEach((id) => {
      if (selSet.has(id)) movingIds.push(id);
      else kept.push(id);
    });
    if (gIdx === targetGroupIdx) {
      const clamped = Math.max(0, Math.min(kept.length, targetPosInGroup));
      kept.splice(clamped, 0, ...movingIds);
    }
    if (kept.length > 0 || gIdx === targetGroupIdx) {
      nextGroups.push(kept);
    }
  });

  if (targetGroupIdx >= state.localGroups.length) {
    nextGroups.push(movingIds);
  }

  state.localGroups = nextGroups.filter((g) => g.length > 0);
  state.selectedCardIds = [];
  persistArrangement();
  render();
}

function handleSortHand() {
  audio.cancelSpeech();
  const ts = state.tableState;
  if (!ts || !ts.myHand) return;
  const sorted = sortHandBySuitAndRank(ts.myHand);
  state.localGroups = sorted.map((g) => g.map((c) => c.id));
  state.selectedCardIds = [];
  persistArrangement();
  render();
}

function handleNewGroup() {
  if (state.selectedCardIds.length === 0) return;
  const selSet = new Set(state.selectedCardIds);
  const moved = [];
  const remaining = state.localGroups
    .map((g) =>
      g.filter((id) => {
        if (selSet.has(id)) {
          moved.push(id);
          return false;
        }
        return true;
      })
    )
    .filter((g) => g.length > 0);
  remaining.push(moved);
  state.localGroups = remaining;
  state.selectedCardIds = [];
  persistArrangement();
  render();
}

function handleUngroup() {
  const flat = state.localGroups.flat();
  state.localGroups = flat.length > 0 ? [flat] : [];
  state.selectedCardIds = [];
  persistArrangement();
  render();
}

function openFinishReview(finishCardId) {
  const ts = state.tableState;
  if (!ts || ts.status !== 'PLAYING' || ts.mySeatIndex !== ts.activeTurnSeatIndex || ts.turnPhase !== 'DISCARD') {
    showToast('Draw a card first before finishing!');
    return;
  }
  const byId = new Map(ts.myHand.map((c) => [c.id, c]));
  const finishCard = byId.get(finishCardId);
  if (!finishCard) return;

  let restoreGroupIdx = 0;
  let restoreCardIdx = 0;
  const remainingGroups = [];
  state.localGroups.forEach((g, gIdx) => {
    const idx = g.indexOf(finishCardId);
    if (idx !== -1) {
      restoreGroupIdx = gIdx;
      restoreCardIdx = idx;
    }
    const filtered = g.filter((id) => id !== finishCardId);
    if (filtered.length > 0) remainingGroups.push(filtered);
  });

  const groupsCards = remainingGroups.map((g) => g.map((id) => byId.get(id)).filter(Boolean));
  const validation = validateDeclaration(groupsCards, ts.wildRank);
  state.finishReview = {
    finishCardId,
    finishCard,
    remainingGroups,
    groupsCards,
    restoreGroupIdx,
    restoreCardIdx,
    validation,
  };
  render();
}

function renderLobby() {
  const today = new Date().toISOString().slice(0, 10);
  const canBonus = state.profile.lastBonusDate !== today;
  const modes = [
    { id: GAME_MODES.POINTS, title: t('points'), sub: t('pointsSub') },
    { id: GAME_MODES.POOL_101, title: t('pool101'), sub: t('pool101Sub') },
    { id: GAME_MODES.POOL_201, title: t('pool201'), sub: t('pool201Sub') },
    { id: GAME_MODES.DEALS_3, title: t('deals3'), sub: t('deals3Sub') },
    { id: GAME_MODES.DEALS_6, title: t('deals6'), sub: t('deals6Sub') },
  ];

  return `<div class="lobby-shell">
    <div class="topbar">
      <h1 class="brand-title">${t('brand')}</h1>
      <div class="pill-controls">
        <button class="icon-pill ${audio.muted ? '' : 'active'}" id="btn-sound">
          ${audio.muted ? '🔇' : '🔊'} ${audio.muted ? t('soundOff') : t('soundOn')}
        </button>
        <button class="icon-pill ${audio.spokenCards ? 'active' : ''}" id="btn-voice">
          🗣️ ${audio.spokenCards ? t('voiceOn') : t('voiceOff')}
        </button>
        <button class="icon-pill active" id="btn-lang">
          ${state.lang === 'en' ? 'EN / हिं' : 'हिं / EN'}
        </button>
      </div>
    </div>

    <div class="hero-card">
      <img class="hero-img" src="/assets/lobby-banner.jpg" alt="Velvet Rummy Den" />
      <div class="hero-overlay">
        <div class="guest-badge">
          <div class="avatar-ring">${renderAvatarSvg(state.profile.avatarId, state.profile.name)}</div>
          <div class="guest-meta">
            <div class="name">${state.profile.name}</div>
            <div class="chips-value">🪙 ${state.profile.chips.toLocaleString()} <span style="font-size:11px;color:#9eb5a8">${t('chips')}</span></div>
          </div>
        </div>
        <button class="bonus-btn" id="btn-bonus" ${canBonus ? '' : 'disabled'}>
          ${canBonus ? t('dailyBonus') : t('bonusClaimed')}
        </button>
      </div>
    </div>

    <div class="panel">
      <div class="section-label">${t('chooseMode')}</div>
      <div class="mode-grid">
        ${modes
          .map(
            (m) => `<button class="choice-btn ${state.selectedMode === m.id ? 'selected' : ''}" data-mode="${m.id}">
              <div class="title">${m.title}</div>
              <div class="sub">${m.sub}</div>
            </button>`
          )
          .join('')}
      </div>
    </div>

    <div class="panel">
      <div class="section-label">${t('chooseSeats')}</div>
      <div class="seat-row">
        ${[2, 3, 4, 5, 6]
          .map(
            (n) => `<button class="seat-chip ${state.selectedSeats === n ? 'selected' : ''}" data-seats="${n}">${n}</button>`
          )
          .join('')}
      </div>
    </div>

    <button class="play-now-btn" id="btn-play-now">${t('playNow')}</button>
    <button class="cpu-btn" id="btn-cpu">${t('cpuPractice')}</button>
  </div>`;
}

function renderTable() {
  const ts = state.tableState;
  const isMyTurn = ts.status === 'PLAYING' && ts.activeTurnSeatIndex === ts.mySeatIndex;
  const mySeat = ts.seats[ts.mySeatIndex] || ts.seats[0];
  const opponents = ts.seats.filter((s) => s.playerId !== state.profile.playerId);
  const byId = new Map((ts.myHand || []).map((c) => [c.id, c]));

  const connColor =
    state.connectionStatus === 'CONNECTED'
      ? ''
      : state.connectionStatus === 'CONNECTING' || state.connectionStatus === 'RECONNECTING'
      ? 'warn'
      : 'err';
  const connText = state.isOfflineCpu ? 'Offline CPU Practice' : state.connectionStatus;

  const countdownSec = ts.countdownEndsAt ? Math.max(0, Math.ceil((ts.countdownEndsAt - Date.now()) / 1000)) : null;

  return `<div class="table-page">
    <div class="table-hud">
      <div class="btn-group">
        <button class="tbl-btn" id="btn-back-lobby">← ${t('backLobby')}</button>
        <span class="status-badge"><span class="status-dot ${connColor}"></span>${connText}</span>
        <span class="status-badge">${ts.mode} · ${ts.seats.length}/${ts.maxSeats} · Deal ${ts.dealNumber || 1}/${ts.totalDeals}</span>
      </div>
      <div class="btn-group">
        <button class="tbl-btn" id="btn-tbl-sound">${audio.muted ? '🔇' : '🔊'}</button>
        <button class="tbl-btn" id="btn-tbl-voice">${audio.spokenCards ? '🗣️' : '🤫'}</button>
        <button class="tbl-btn" id="btn-tbl-lang">${state.lang.toUpperCase()}</button>
      </div>
    </div>

    <div class="felt-stage">
      <div class="oval-rim"><div class="oval-felt">
        <img class="felt-crest" src="/assets/table-crest.jpg" alt="" />
      </div></div>

      <div class="opponents-arc">
        ${opponents
          .map((opp) => {
            const active = ts.status === 'PLAYING' && ts.activeTurnSeatIndex === opp.seatIndex;
            return `<div class="seat-pod ${active ? 'active-turn' : ''} ${opp.isDropped || opp.isEliminated ? 'dropped' : ''}">
              <div class="avatar-ring sm">${renderAvatarSvg(opp.avatarId, opp.name)}</div>
              <div class="seat-info">
                <div class="sname">${opp.name} ${opp.connected === false ? '(Reconnecting)' : ''}</div>
                <div class="smeta">${opp.cardCount}🃏 · ${opp.matchPoints}pt · 🪙${opp.chips}</div>
              </div>
            </div>`;
          })
          .join('')}
      </div>

      <div class="center-arena">
        ${
          ts.status === 'WAITING'
            ? `<div class="countdown-banner">
                <div style="font-size:13px;font-weight:700;">
                  ${countdownSec !== null ? t('searchingPlayers') : t('waitingLone')}
                </div>
                ${countdownSec !== null ? `<div class="countdown-num">${t('dealIn')} ${countdownSec}s</div>` : ''}
                <div style="font-size:11px;color:#9eb5a8;margin-top:4px;">${ts.seats.length} / ${ts.maxSeats} seated</div>
              </div>`
            : `<div class="piles-row">
                <div class="pile-box">
                  <div class="pile-label">${t('jokerLabel')}</div>
                  <div class="joker-flip">${ts.jokerIndicator ? renderCardHtml(ts.jokerIndicator, { wildRank: ts.wildRank, draggable: false }) : ''}</div>
                </div>
                <div class="pile-box">
                  <div class="pile-label">${t('stockLabel')} (${ts.closedStockCount})</div>
                  <div class="playing-card card-back" id="pile-closed">★<span style="font-size:10px;margin-top:2px;">DRAW</span></div>
                </div>
                <div class="pile-box">
                  <div class="pile-label">${t('openLabel')} (${ts.openPileCount})</div>
                  <div id="pile-open">${ts.openPileTop ? renderCardHtml(ts.openPileTop, { wildRank: ts.wildRank, draggable: false }) : '<div class="card-slot">EMPTY</div>'}</div>
                </div>
                <div class="pile-box">
                  <div class="pile-label">${t('finishLabel')}</div>
                  <div class="card-slot finish-target ${isMyTurn && ts.turnPhase === 'DISCARD' && state.selectedCardIds.length === 1 ? 'ready' : ''}" id="pile-finish">
                    🏆<span style="font-size:10px;">${t('finishLabel')}</span>
                  </div>
                </div>
              </div>
              <div class="status-badge" style="border:1px solid rgba(229,184,75,0.4);">
                ${
                  isMyTurn
                    ? ts.turnPhase === 'DRAW'
                      ? t('yourTurnDraw')
                      : t('yourTurnDiscard')
                    : `${t('opponentTurn')} ${ts.seats[ts.activeTurnSeatIndex]?.name || ''}`
                }
              </div>`
        }
      </div>

      <div class="player-dock">
        <div class="action-bar">
          <div class="seat-pod ${isMyTurn ? 'active-turn' : ''}">
            <div class="avatar-ring sm">${renderAvatarSvg(mySeat.avatarId, mySeat.name)}</div>
            <div class="seat-info">
              <div class="sname">${mySeat.name}</div>
              <div class="smeta">🪙${mySeat.chips} · ${mySeat.matchPoints}pt</div>
            </div>
          </div>

          <div class="btn-group">
            <button class="tbl-btn" id="btn-sort" ${ts.myHand?.length ? '' : 'disabled'}>${t('sort')}</button>
            <button class="tbl-btn" id="btn-new-group" ${state.selectedCardIds.length > 0 ? '' : 'disabled'}>${t('newGroup')}</button>
            <button class="tbl-btn" id="btn-move-here" ${state.selectedCardIds.length > 0 ? '' : 'disabled'}>${t('moveHere')}</button>
            <button class="tbl-btn" id="btn-ungroup" ${state.localGroups.length > 1 ? '' : 'disabled'}>${t('ungroup')}</button>
            <button class="tbl-btn gold" id="btn-discard" ${isMyTurn && ts.turnPhase === 'DISCARD' && state.selectedCardIds.length === 1 ? '' : 'disabled'}>${t('discard')}</button>
            <button class="tbl-btn danger" id="btn-drop" ${isMyTurn && ts.turnPhase === 'DRAW' && getDropPenalty(ts.mode, mySeat.hasDrawnThisHand).allowed ? '' : 'disabled'}>${t('drop')}</button>
          </div>
        </div>

        <div class="hand-tray" id="hand-tray">
          ${state.localGroups
            .map((gIds, gIdx) => {
              const cards = gIds.map((id) => byId.get(id)).filter(Boolean);
              const gType = classifyGroup(cards, ts.wildRank);
              return `<div class="card-group-wrap" data-group-idx="${gIdx}">
                <div class="card-group-cards">
                  ${cards
                    .map(
                      (c, cIdx) => `<div class="gap-slot ${state.selectedCardIds.length > 0 ? 'has-selection' : ''}" data-gap="${gIdx}:${cIdx}"></div>` +
                        renderCardHtml(c, {
                          selected: state.selectedCardIds.includes(c.id),
                          wildRank: ts.wildRank,
                          isNew: c.id === ts.myLastDrawnCardId,
                          dealAnim: state.shouldAnimateDeal,
                        })
                    )
                    .join('')}
                  <div class="gap-slot ${state.selectedCardIds.length > 0 ? 'has-selection' : ''}" data-gap="${gIdx}:${cards.length}"></div>
                </div>
                <span class="group-tag ${gType}">${gType.replace('_', ' ')}</span>
              </div>`;
            })
            .join('')}
          ${
            state.selectedCardIds.length > 0
              ? `<div class="gap-slot has-selection" style="width:28px;border:1px dashed #e5b84b;border-radius:8px;" data-gap="${state.localGroups.length}:0">+</div>`
              : ''
          }
        </div>
      </div>
    </div>
    ${renderFinishModal()}
    ${renderDropModal()}
    ${renderResultModal()}
  </div>`;
}

function renderFinishModal() {
  const fr = state.finishReview;
  if (!fr) return '';
  const ts = state.tableState;
  return `<div class="modal-backdrop">
    <div class="modal-card">
      <h2 class="modal-title">${t('reviewTitle')}</h2>
      <p style="margin:0;font-size:13px;color:#cbd5e1;">${t('reviewNote')}</p>
      <div style="display:flex;align-items:center;gap:12px;">
        <div>
          <div class="pile-label">Finish Discard</div>
          ${renderCardHtml(fr.finishCard, { wildRank: ts.wildRank, draggable: false })}
        </div>
        <div style="flex:1;padding:10px;border-radius:12px;background:${fr.validation.isValid ? 'rgba(24,169,87,0.2)' : 'rgba(211,47,47,0.2)'};border:1px solid ${fr.validation.isValid ? '#4ade80' : '#ef5350'};">
          <strong>${fr.validation.isValid ? 'VALID DECLARATION' : 'INVALID DECLARATION (80 pt penalty!)'}</strong>
          <div style="font-size:12px;margin-top:4px;">${state.lang === 'hi' ? fr.validation.reasonHi : fr.validation.reasonEn}</div>
        </div>
      </div>
      <div class="review-groups">
        ${fr.validation.groupResults
          .map(
            (gr) => `<div class="card-group-wrap">
              <div class="card-group-cards" style="gap:3px;">
                ${gr.cards.map((c) => renderCardHtml(c, { wildRank: ts.wildRank, draggable: false })).join('')}
              </div>
              <span class="group-tag ${gr.type}">${gr.type.replace('_', ' ')}</span>
            </div>`
          )
          .join('')}
      </div>
      <div class="btn-group" style="justify-content:flex-end;">
        <button class="tbl-btn" id="btn-finish-cancel">${t('cancel')}</button>
        <button class="tbl-btn primary" id="btn-finish-confirm">${t('confirmDeclare')}</button>
      </div>
    </div>
  </div>`;
}

function renderDropModal() {
  const dc = state.dropConfirm;
  if (!dc) return '';
  return `<div class="modal-backdrop">
    <div class="modal-card" style="max-width:420px;">
      <h2 class="modal-title">${t('dropConfirmTitle')}</h2>
      <p style="margin:0;font-size:14px;">${state.lang === 'hi' ? dc.reasonHi : dc.reasonEn}</p>
      <div class="btn-group" style="justify-content:flex-end;">
        <button class="tbl-btn" id="btn-drop-cancel">${t('cancel')}</button>
        <button class="tbl-btn danger" id="btn-drop-confirm">${t('dropConfirmBtn')}</button>
      </div>
    </div>
  </div>`;
}

function renderResultModal() {
  const ts = state.tableState;
  if (!ts || (ts.status !== 'HAND_RESULT' && ts.status !== 'MATCH_RESULT') || !ts.result) return '';
  const r = ts.result;
  const iWon = r.matchFinished
    ? r.matchWinnerId === state.profile.playerId
    : r.handWinnerId === state.profile.playerId;

  return `<div class="modal-backdrop">
    <div class="modal-card">
      <h2 class="modal-title">${iWon ? t('congrats') : t('handOver')}</h2>
      <div style="font-size:14px;font-weight:700;">
        🏆 ${r.matchFinished ? r.matchWinnerName : r.handWinnerName} (${r.matchFinished ? 'Match Winner' : `Deal ${r.dealNumber}/${r.totalDeals}`})
      </div>
      <div class="review-groups">
        ${(r.winningGroups || [])
          .map(
            (g) => `<div class="card-group-wrap">
              <div class="card-group-cards" style="gap:3px;">
                ${g.map((c) => renderCardHtml(c, { wildRank: ts.wildRank, draggable: false })).join('')}
              </div>
              <span class="group-tag ${classifyGroup(g, ts.wildRank)}">${classifyGroup(g, ts.wildRank).replace('_', ' ')}</span>
            </div>`
          )
          .join('')}
      </div>
      <table class="score-table">
        <thead><tr><th>Player</th><th>Hand Pts</th><th>Total Pts</th></tr></thead>
        <tbody>
          ${r.handScores
            .map(
              (s) => `<tr>
                <td>${s.name} ${s.isWinner ? '👑' : ''}</td>
                <td>${s.handPoints}</td>
                <td>${s.matchPoints}</td>
              </tr>`
            )
            .join('')}
        </tbody>
      </table>
      ${
        r.matchFinished
          ? `<div style="display:flex;justify-content:space-between;background:rgba(229,184,75,0.12);padding:10px 14px;border-radius:12px;border:1px solid var(--gold);">
              <div><div style="font-size:11px;color:#9eb5a8">${t('grossReward')}</div><strong>🪙 ${r.grossReward}</strong></div>
              <div><div style="font-size:11px;color:#9eb5a8">${t('commission')}</div><strong>-🪙 ${r.commission}</strong></div>
              <div><div style="font-size:11px;color:#9eb5a8">${t('netReward')}</div><strong style="color:var(--gold)">+🪙 ${r.netReward}</strong></div>
            </div>`
          : ''
      }
      <div class="btn-group" style="justify-content:flex-end;">
        <button class="tbl-btn" id="btn-res-lobby">${t('backLobby')}</button>
        <button class="tbl-btn primary" id="btn-res-next">${r.matchFinished ? t('playAgain') : t('nextDeal')}</button>
      </div>
    </div>
  </div>`;
}

function render() {
  const root = document.getElementById('app');
  root.innerHTML =
    (state.screen === 'LOBBY' ? renderLobby() : renderTable()) +
    (state.toast ? `<div class="toast">${state.toast}</div>` : '');
  bindEvents();
}

function bindEvents() {
  if (state.screen === 'LOBBY') {
    document.getElementById('btn-sound')?.addEventListener('click', () => {
      audio.unlock();
      audio.muted = !audio.muted;
      render();
    });
    document.getElementById('btn-voice')?.addEventListener('click', () => {
      audio.unlock();
      audio.spokenCards = !audio.spokenCards;
      render();
    });
    document.getElementById('btn-lang')?.addEventListener('click', () => {
      state.lang = state.lang === 'en' ? 'hi' : 'en';
      localStorage.setItem('vrd_lang', state.lang);
      render();
    });
    document.getElementById('btn-bonus')?.addEventListener('click', () => {
      audio.unlock();
      const today = new Date().toISOString().slice(0, 10);
      if (state.profile.lastBonusDate !== today) {
        state.profile.chips += 1000;
        state.profile.lastBonusDate = today;
        localStorage.setItem('vrd_chips', String(state.profile.chips));
        localStorage.setItem('vrd_bonus_date', today);
        audio.playWinCheer();
        render();
      }
    });
    document.querySelectorAll('[data-mode]').forEach((el) => {
      el.addEventListener('click', () => {
        state.selectedMode = el.dataset.mode;
        render();
      });
    });
    document.querySelectorAll('[data-seats]').forEach((el) => {
      el.addEventListener('click', () => {
        state.selectedSeats = Number(el.dataset.seats);
        render();
      });
    });
    document.getElementById('btn-play-now')?.addEventListener('click', joinMultiplayerTable);
    document.getElementById('btn-cpu')?.addEventListener('click', startCpuPractice);
    return;
  }

  // Table events
  document.getElementById('btn-back-lobby')?.addEventListener('click', exitToLobby);
  document.getElementById('btn-res-lobby')?.addEventListener('click', exitToLobby);
  document.getElementById('btn-tbl-sound')?.addEventListener('click', () => {
    audio.unlock();
    audio.muted = !audio.muted;
    render();
  });
  document.getElementById('btn-tbl-voice')?.addEventListener('click', () => {
    audio.unlock();
    audio.spokenCards = !audio.spokenCards;
    render();
  });
  document.getElementById('btn-tbl-lang')?.addEventListener('click', () => {
    state.lang = state.lang === 'en' ? 'hi' : 'en';
    localStorage.setItem('vrd_lang', state.lang);
    render();
  });

  document.getElementById('pile-closed')?.addEventListener('click', () => {
    audio.unlock();
    sendGameAction({ type: 'DRAW_CLOSED' });
  });
  document.getElementById('pile-open')?.addEventListener('click', () => {
    audio.unlock();
    sendGameAction({ type: 'DRAW_OPEN' });
  });
  const finishEl = document.getElementById('pile-finish');
  if (finishEl) {
    finishEl.addEventListener('click', () => {
      if (state.selectedCardIds.length === 1) {
        openFinishReview(state.selectedCardIds[0]);
      } else {
        showToast('Select 1 card to finish');
      }
    });
    finishEl.addEventListener('dragover', (e) => e.preventDefault());
    finishEl.addEventListener('drop', (e) => {
      e.preventDefault();
      if (state.dragCardId) {
        openFinishReview(state.dragCardId);
        state.dragCardId = null;
      }
    });
  }

  document.getElementById('btn-sort')?.addEventListener('click', handleSortHand);
  document.getElementById('btn-new-group')?.addEventListener('click', handleNewGroup);
  document.getElementById('btn-move-here')?.addEventListener('click', () => {
    moveSelectedTo(state.localGroups.length, 0);
  });
  document.getElementById('btn-ungroup')?.addEventListener('click', handleUngroup);
  document.getElementById('btn-discard')?.addEventListener('click', () => {
    if (state.selectedCardIds.length === 1) {
      sendGameAction({ type: 'DISCARD', cardId: state.selectedCardIds[0] });
    }
  });
  document.getElementById('btn-drop')?.addEventListener('click', () => {
    const ts = state.tableState;
    const mySeat = ts.seats[ts.mySeatIndex];
    const penalty = getDropPenalty(ts.mode, mySeat?.hasDrawnThisHand);
    if (penalty.allowed) {
      state.dropConfirm = penalty;
      render();
    }
  });

  document.getElementById('btn-drop-cancel')?.addEventListener('click', () => {
    state.dropConfirm = null;
    render();
  });
  document.getElementById('btn-drop-confirm')?.addEventListener('click', () => {
    state.dropConfirm = null;
    sendGameAction({ type: 'DROP' });
  });

  document.getElementById('btn-finish-cancel')?.addEventListener('click', () => {
    // Cancel must put the selected card back in its exact position
    state.finishReview = null;
    state.selectedCardIds = [];
    render();
  });
  document.getElementById('btn-finish-confirm')?.addEventListener('click', () => {
    const fr = state.finishReview;
    if (!fr) return;
    state.finishReview = null;
    state.selectedCardIds = [];
    sendGameAction({
      type: 'SUBMIT_FINISH',
      finishCardId: fr.finishCardId,
      groups: fr.remainingGroups,
    });
  });

  document.getElementById('btn-res-next')?.addEventListener('click', () => {
    const r = state.tableState?.result;
    sendGameAction({ type: r?.matchFinished ? 'PLAY_AGAIN' : 'NEXT_DEAL' });
  });

  // Hand cards tap + drag
  document.querySelectorAll('#hand-tray .playing-card[data-card-id]').forEach((el) => {
    const cardId = el.dataset.cardId;
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      audio.unlock();
      const idx = state.selectedCardIds.indexOf(cardId);
      if (idx >= 0) {
        state.selectedCardIds.splice(idx, 1);
      } else {
        state.selectedCardIds.push(cardId);
        const card = state.tableState?.myHand?.find((c) => c.id === cardId);
        if (card) audio.speakCard(card, state.lang);
      }
      render();
    });
    el.addEventListener('dragstart', () => {
      state.dragCardId = cardId;
    });
  });

  // Gap slots tap + drop
  document.querySelectorAll('#hand-tray .gap-slot[data-gap]').forEach((gap) => {
    const [gIdx, cIdx] = gap.dataset.gap.split(':').map(Number);
    gap.addEventListener('click', () => {
      if (state.selectedCardIds.length > 0) {
        moveSelectedTo(gIdx, cIdx);
      }
    });
    gap.addEventListener('dragover', (e) => {
      e.preventDefault();
      gap.classList.add('drag-over');
    });
    gap.addEventListener('dragleave', () => {
      gap.classList.remove('drag-over');
    });
    gap.addEventListener('drop', (e) => {
      e.preventDefault();
      gap.classList.remove('drag-over');
      if (state.dragCardId) {
        state.selectedCardIds = [state.dragCardId];
        state.dragCardId = null;
        moveSelectedTo(gIdx, cIdx);
      }
    });
  });
}

async function exitToLobby() {
  audio.cancelSpeech();
  if (!state.isOfflineCpu) {
    if (state.ws) {
      state.ws.close();
      state.ws = null;
    }
    fetch('/api/tables/leave', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: state.profile.playerId }),
    }).catch(() => {});
  }
  state.screen = 'LOBBY';
  state.tableState = null;
  state.finishReview = null;
  state.dropConfirm = null;
  state.connectionStatus = 'IDLE';
  render();
}

clearInterval(countdownInterval);
countdownInterval = setInterval(() => {
  if (state.screen === 'TABLE' && state.tableState?.status === 'WAITING' && state.tableState.countdownEndsAt) {
    render();
  }
}, 500);

render();
