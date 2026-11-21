import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  GAME_MODES,
  createDeck106,
  secureShuffle,
  dealHandFromDeck,
  validateDeclaration,
  calculateDeadwoodPoints,
  computeCommissionAndNet,
  getDropPenalty,
  isWildCard,
} from './engine/rummyEngine.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PUBLIC_DIR = path.join(__dirname, 'public');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
};

export function createRummyServer(options = {}) {
  const countdownMs = options.countdownMs ?? 20_000;
  const turnTimeoutMs = options.turnTimeoutMs ?? 45_000;
  const deckProvider = options.deckProvider ?? (() => secureShuffle(createDeck106()));

  const tables = new Map();
  const profiles = new Map(); // playerId -> { playerId, reconnectToken, name, avatarId, chips }
  const playerToTable = new Map(); // playerId -> tableId
  const wsClients = new Map(); // socket -> { playerId, tableId }

  function getOrCreateProfile({ playerId, reconnectToken, name, avatarId, chips }) {
    let existing = profiles.get(playerId);
    if (!existing) {
      existing = {
        playerId,
        reconnectToken: reconnectToken || crypto.randomUUID(),
        name: name || `Guest_${playerId.slice(0, 4)}`,
        avatarId: Number.isInteger(avatarId) ? avatarId : 0,
        chips: typeof chips === 'number' && chips >= 0 ? chips : 5000,
      };
      profiles.set(playerId, existing);
    } else {
      if (name) existing.name = name;
      if (Number.isInteger(avatarId)) existing.avatarId = avatarId;
    }
    return existing;
  }

  function createTable({ mode, maxSeats, stake }) {
    const id = `tbl_${crypto.randomBytes(4).toString('hex')}`;
    const totalDeals = mode === GAME_MODES.DEALS_3 ? 3 : mode === GAME_MODES.DEALS_6 ? 6 : 1;
    const table = {
      id,
      mode,
      maxSeats: Math.max(2, Math.min(6, Number(maxSeats) || 4)),
      stake: Math.max(10, Number(stake) || 100),
      totalDeals,
      dealNumber: 0,
      dealSequenceId: 0,
      status: 'WAITING', // WAITING, PLAYING, HAND_RESULT, MATCH_RESULT
      seats: [],
      countdownStartedAt: null,
      countdownEndsAt: null,
      countdownTimer: null,
      turnTimer: null,
      activeTurnSeatIndex: 0,
      turnPhase: 'DRAW', // DRAW, DISCARD
      drawnFromOpenCardId: null,
      jokerIndicator: null,
      wildRank: 1,
      openPile: [],
      closedStock: [],
      lastResult: null,
      settlementApplied: false,
      matchEntryDeducted: false,
      createdAt: Date.now(),
    };
    tables.set(id, table);
    return table;
  }

  function findWaitingTable(mode, maxSeats, stake) {
    for (const table of tables.values()) {
      if (
        table.status === 'WAITING' &&
        table.mode === mode &&
        table.maxSeats === maxSeats &&
        table.stake === stake &&
        table.seats.length < table.maxSeats
      ) {
        return table;
      }
    }
    return null;
  }

  function clearCountdown(table) {
    if (table.countdownTimer) {
      clearTimeout(table.countdownTimer);
      table.countdownTimer = null;
    }
  }

  function clearTurnTimer(table) {
    if (table.turnTimer) {
      clearTimeout(table.turnTimer);
      table.turnTimer = null;
    }
  }

  function startCountdownIfReady(table) {
    if (table.status !== 'WAITING') return;
    if (table.seats.length === 0) {
      clearCountdown(table);
      table.countdownStartedAt = null;
      table.countdownEndsAt = null;
      return;
    }
    if (table.countdownEndsAt !== null) {
      // Countdown already running; do not reset or deal early even if table fills!
      return;
    }
    const now = Date.now();
    table.countdownStartedAt = now;
    table.countdownEndsAt = now + countdownMs;
    clearCountdown(table);
    table.countdownTimer = setTimeout(() => {
      onCountdownExpired(table.id);
    }, countdownMs);
  }

  function onCountdownExpired(tableId) {
    const table = tables.get(tableId);
    if (!table || table.status !== 'WAITING') return;
    clearCountdown(table);

    if (table.seats.length >= 2) {
      startDeal(table);
    } else {
      // Lone player continues waiting without dealing!
      table.countdownStartedAt = null;
      table.countdownEndsAt = null;
      broadcastTable(table);
    }
  }

  function startDeal(table) {
    clearCountdown(table);
    clearTurnTimer(table);

    // Deduct match entry stake once for Pool & Deals modes
    if (!table.matchEntryDeducted && table.mode !== GAME_MODES.POINTS) {
      for (const seat of table.seats) {
        const prof = profiles.get(seat.playerId);
        const deduction = Math.min(prof?.chips ?? seat.chips, table.stake);
        if (prof) prof.chips -= deduction;
        seat.chips = prof ? prof.chips : Math.max(0, seat.chips - deduction);
      }
      table.matchEntryDeducted = true;
    }

    const activeSeats = table.seats.filter((s) => !s.isEliminated);
    if (activeSeats.length < 2) {
      table.status = 'WAITING';
      broadcastTable(table);
      return;
    }

    table.dealNumber += 1;
    table.dealSequenceId += 1;
    table.status = 'PLAYING';
    table.settlementApplied = false;
    table.lastResult = null;
    table.drawnFromOpenCardId = null;
    table.turnPhase = 'DRAW';

    const deck = deckProvider(table);
    const dealt = dealHandFromDeck(deck, activeSeats.length);
    table.jokerIndicator = dealt.jokerIndicator;
    table.wildRank = dealt.wildRank;
    table.openPile = dealt.openPile;
    table.closedStock = dealt.closedStock;

    activeSeats.forEach((seat, idx) => {
      seat.hand = dealt.hands[idx];
      seat.arrangedGroups = [seat.hand.map((c) => c.id)];
      seat.isDropped = false;
      seat.hasDrawnThisHand = false;
      seat.lastDrawnCardId = null;
      seat.wrongDeclaration = false;
      seat.lastHandPoints = 0;
    });

    const firstActiveIdx = table.seats.findIndex((s) => !s.isEliminated);
    table.activeTurnSeatIndex = firstActiveIdx >= 0 ? firstActiveIdx : 0;
    scheduleTurnTimer(table);
    broadcastTable(table);
  }

  function scheduleTurnTimer(table) {
    clearTurnTimer(table);
    if (table.status !== 'PLAYING') return;
    table.turnTimer = setTimeout(() => {
      autoAdvanceTurn(table.id);
    }, turnTimeoutMs);
  }

  function autoAdvanceTurn(tableId) {
    const table = tables.get(tableId);
    if (!table || table.status !== 'PLAYING') return;
    const seat = table.seats[table.activeTurnSeatIndex];
    if (!seat) return;
    if (table.turnPhase === 'DRAW') {
      // Auto draw from closed and discard drawn card
      const drawn = popClosedStock(table);
      if (drawn) {
        table.openPile.push(drawn);
      }
    } else if (table.turnPhase === 'DISCARD') {
      // Discard last drawn card (if not from open) or first non-open card
      const candidate =
        seat.hand.find((c) => c.id === seat.lastDrawnCardId && c.id !== table.drawnFromOpenCardId) ||
        seat.hand.find((c) => c.id !== table.drawnFromOpenCardId) ||
        seat.hand[0];
      if (candidate) {
        removeCardFromSeat(seat, candidate.id);
        table.openPile.push(candidate);
      }
    }
    advanceToNextSeat(table);
    broadcastTable(table);
  }

  function popClosedStock(table) {
    if (table.closedStock.length === 0 && table.openPile.length > 1) {
      const top = table.openPile.pop();
      table.closedStock = secureShuffle(table.openPile);
      table.openPile = [top];
    }
    return table.closedStock.pop() || null;
  }

  function removeCardFromSeat(seat, cardId) {
    const idx = seat.hand.findIndex((c) => c.id === cardId);
    if (idx === -1) return null;
    const [removed] = seat.hand.splice(idx, 1);
    seat.arrangedGroups = seat.arrangedGroups
      .map((g) => g.filter((id) => id !== cardId))
      .filter((g) => g.length > 0);
    if (seat.lastDrawnCardId === cardId) seat.lastDrawnCardId = null;
    return removed;
  }

  function addCardToSeatArrangement(seat, card) {
    seat.hand.push(card);
    seat.lastDrawnCardId = card.id;
    // Append drawn card as a clearly visible trailing slot without disturbing existing groups
    seat.arrangedGroups = [...seat.arrangedGroups, [card.id]];
  }

  function advanceToNextSeat(table) {
    const total = table.seats.length;
    for (let step = 1; step <= total; step++) {
      const idx = (table.activeTurnSeatIndex + step) % total;
      const candidate = table.seats[idx];
      if (!candidate.isDropped && !candidate.isEliminated) {
        table.activeTurnSeatIndex = idx;
        table.turnPhase = 'DRAW';
        table.drawnFromOpenCardId = null;
        scheduleTurnTimer(table);
        return;
      }
    }
  }

  function settleHand(table, winnerSeat, winningGroups, loserReasonMap = {}) {
    clearTurnTimer(table);
    if (table.settlementApplied) return;
    table.settlementApplied = true;

    const handScores = [];
    let totalPointsPool = 0;

    for (const seat of table.seats) {
      if (seat.isEliminated) continue;
      let pts = 0;
      if (seat.playerId === winnerSeat.playerId) {
        pts = 0;
      } else if (seat.wrongDeclaration) {
        pts = 80;
      } else if (seat.isDropped) {
        pts = seat.lastHandPoints || (table.mode === GAME_MODES.POOL_201 ? 25 : 20);
      } else {
        pts = calculateDeadwoodPoints(seat.hand, table.wildRank);
      }
      seat.lastHandPoints = pts;
      seat.matchPoints += pts;
      totalPointsPool += pts;
      handScores.push({
        playerId: seat.playerId,
        name: seat.name,
        avatarId: seat.avatarId,
        seatIndex: seat.seatIndex,
        handPoints: pts,
        matchPoints: seat.matchPoints,
        isWinner: seat.playerId === winnerSeat.playerId,
        isDropped: seat.isDropped,
        wrongDeclaration: seat.wrongDeclaration,
        reason: loserReasonMap[seat.playerId] || null,
        cards: seat.hand,
      });
    }

    let matchFinished = false;
    let matchWinnerSeat = winnerSeat;
    let grossReward = 0;
    let commission = 0;
    let netReward = 0;

    if (table.mode === GAME_MODES.POINTS) {
      matchFinished = true;
      const pointChipValue = Math.max(1, Math.floor(table.stake / 80));
      let totalLostChips = 0;
      for (const seat of table.seats) {
        if (seat.playerId === winnerSeat.playerId) continue;
        const loss = Math.min(seat.chips, seat.lastHandPoints * pointChipValue);
        seat.chips -= loss;
        const prof = profiles.get(seat.playerId);
        if (prof) prof.chips = seat.chips;
        totalLostChips += loss;
      }
      const settled = computeCommissionAndNet(totalLostChips);
      grossReward = settled.grossReward;
      commission = settled.commission;
      netReward = settled.netReward;
      winnerSeat.chips += netReward;
      const wProf = profiles.get(winnerSeat.playerId);
      if (wProf) wProf.chips = winnerSeat.chips;
    } else if (table.mode === GAME_MODES.POOL_101 || table.mode === GAME_MODES.POOL_201) {
      const limit = table.mode === GAME_MODES.POOL_101 ? 101 : 201;
      for (const seat of table.seats) {
        if (seat.matchPoints >= limit) {
          seat.isEliminated = true;
        }
      }
      const survivors = table.seats.filter((s) => !s.isEliminated);
      if (survivors.length <= 1) {
        matchFinished = true;
        matchWinnerSeat = survivors[0] || [...table.seats].sort((a, b) => a.matchPoints - b.matchPoints)[0];
        const settled = computeCommissionAndNet(table.stake * table.seats.length);
        grossReward = settled.grossReward;
        commission = settled.commission;
        netReward = settled.netReward;
        matchWinnerSeat.chips += netReward;
        const wProf = profiles.get(matchWinnerSeat.playerId);
        if (wProf) wProf.chips = matchWinnerSeat.chips;
      }
    } else {
      // DEALS_3 or DEALS_6
      if (table.dealNumber >= table.totalDeals) {
        matchFinished = true;
        matchWinnerSeat = [...table.seats].sort((a, b) => a.matchPoints - b.matchPoints)[0];
        const settled = computeCommissionAndNet(table.stake * table.seats.length);
        grossReward = settled.grossReward;
        commission = settled.commission;
        netReward = settled.netReward;
        matchWinnerSeat.chips += netReward;
        const wProf = profiles.get(matchWinnerSeat.playerId);
        if (wProf) wProf.chips = matchWinnerSeat.chips;
      }
    }

    table.status = matchFinished ? 'MATCH_RESULT' : 'HAND_RESULT';
    table.lastResult = {
      handWinnerId: winnerSeat.playerId,
      handWinnerName: winnerSeat.name,
      matchWinnerId: matchFinished ? matchWinnerSeat.playerId : null,
      matchWinnerName: matchFinished ? matchWinnerSeat.name : null,
      matchFinished,
      dealNumber: table.dealNumber,
      totalDeals: table.totalDeals,
      winningGroups,
      handScores,
      grossReward,
      commission,
      netReward,
      totalPointsPool,
    };
  }

  function joinOrReconnect({ playerId, reconnectToken, name, avatarId, chips, mode, maxSeats, stake }) {
    if (!playerId) throw new Error('playerId required');
    const profile = getOrCreateProfile({ playerId, reconnectToken, name, avatarId, chips });

    // Check if already seated in an existing table
    const existingTableId = playerToTable.get(playerId);
    if (existingTableId && tables.has(existingTableId)) {
      const existingTable = tables.get(existingTableId);
      const existingSeat = existingTable.seats.find((s) => s.playerId === playerId);
      if (existingSeat && existingTable.status !== 'CLOSED') {
        if (!reconnectToken || profile.reconnectToken === reconnectToken) {
          existingSeat.connected = true;
          broadcastTable(existingTable);
          return { table: existingTable, seat: existingSeat, profile, reconnected: true };
        }
      }
    }

    const reqMode = mode || GAME_MODES.POINTS;
    const reqMaxSeats = Math.max(2, Math.min(6, Number(maxSeats) || 4));
    const reqStake = Math.max(10, Number(stake) || 100);

    let table = findWaitingTable(reqMode, reqMaxSeats, reqStake);
    if (!table) {
      table = createTable({ mode: reqMode, maxSeats: reqMaxSeats, stake: reqStake });
    }

    const seat = {
      playerId: profile.playerId,
      name: profile.name,
      avatarId: profile.avatarId,
      seatIndex: table.seats.length,
      chips: profile.chips,
      matchPoints: 0,
      lastHandPoints: 0,
      isDropped: false,
      isEliminated: false,
      wrongDeclaration: false,
      hasDrawnThisHand: false,
      lastDrawnCardId: null,
      hand: [],
      arrangedGroups: [],
      connected: true,
    };

    table.seats.push(seat);
    playerToTable.set(playerId, table.id);

    startCountdownIfReady(table);
    broadcastTable(table);
    return { table, seat, profile, reconnected: false };
  }

  function leaveTable(playerId) {
    const tableId = playerToTable.get(playerId);
    if (!tableId) return;
    const table = tables.get(tableId);
    playerToTable.delete(playerId);
    if (!table) return;

    if (table.status === 'WAITING') {
      table.seats = table.seats.filter((s) => s.playerId !== playerId);
      table.seats.forEach((s, i) => {
        s.seatIndex = i;
      });
      if (table.seats.length === 0) {
        clearCountdown(table);
        tables.delete(table.id);
      } else {
        broadcastTable(table);
      }
    } else {
      const seat = table.seats.find((s) => s.playerId === playerId);
      if (seat) {
        seat.connected = false;
        broadcastTable(table);
      }
    }
  }

  function handleAction(playerId, payload) {
    const tableId = payload.tableId || playerToTable.get(playerId);
    const table = tables.get(tableId);
    if (!table) throw new Error('Table not found');
    const seatIdx = table.seats.findIndex((s) => s.playerId === playerId);
    if (seatIdx === -1) throw new Error('Player not seated at table');
    const seat = table.seats[seatIdx];

    const type = payload.type;

    if (type === 'SAVE_ARRANGEMENT') {
      const groups = payload.groups;
      if (!Array.isArray(groups)) throw new Error('groups must be an array');
      const flatIds = groups.flat();
      const handIds = new Set(seat.hand.map((c) => c.id));
      if (flatIds.length !== handIds.size || new Set(flatIds).size !== handIds.size) {
        throw new Error('Arrangement must contain exact hand card IDs');
      }
      for (const id of flatIds) {
        if (!handIds.has(id)) throw new Error(`Unknown card id in arrangement: ${id}`);
      }
      seat.arrangedGroups = groups;
      return getTableViewForPlayer(table, playerId);
    }

    if (type === 'NEXT_DEAL' || type === 'PLAY_AGAIN') {
      if (table.status === 'HAND_RESULT') {
        startDeal(table);
        return getTableViewForPlayer(table, playerId);
      }
      if (table.status === 'MATCH_RESULT') {
        table.dealNumber = 0;
        table.matchEntryDeducted = false;
        table.seats.forEach((s) => {
          s.matchPoints = 0;
          s.isEliminated = false;
          s.isDropped = false;
        });
        startDeal(table);
        return getTableViewForPlayer(table, playerId);
      }
      throw new Error('Cannot start next deal in current state');
    }

    if (table.status !== 'PLAYING') {
      throw new Error(`Action ${type} invalid when table status is ${table.status}`);
    }
    if (table.activeTurnSeatIndex !== seatIdx) {
      throw new Error('Not your turn');
    }

    if (type === 'DRAW_CLOSED') {
      if (table.turnPhase !== 'DRAW') throw new Error('Already drew this turn');
      const card = popClosedStock(table);
      if (!card) throw new Error('Stock exhausted');
      addCardToSeatArrangement(seat, card);
      seat.hasDrawnThisHand = true;
      table.turnPhase = 'DISCARD';
      table.drawnFromOpenCardId = null;
      scheduleTurnTimer(table);
      broadcastTable(table);
      return getTableViewForPlayer(table, playerId);
    }

    if (type === 'DRAW_OPEN') {
      if (table.turnPhase !== 'DRAW') throw new Error('Already drew this turn');
      if (table.openPile.length === 0) throw new Error('Open pile is empty');
      const card = table.openPile.pop();
      addCardToSeatArrangement(seat, card);
      seat.hasDrawnThisHand = true;
      table.turnPhase = 'DISCARD';
      table.drawnFromOpenCardId = card.id;
      scheduleTurnTimer(table);
      broadcastTable(table);
      return getTableViewForPlayer(table, playerId);
    }

    if (type === 'DISCARD') {
      if (table.turnPhase !== 'DISCARD') throw new Error('Must draw a card before discarding');
      const cardId = payload.cardId;
      if (cardId === table.drawnFromOpenCardId) {
        throw new Error('Cannot immediately discard the exact card just drawn from the open pile');
      }
      const removed = removeCardFromSeat(seat, cardId);
      if (!removed) throw new Error('Card not in your hand');
      table.openPile.push(removed);
      advanceToNextSeat(table);
      broadcastTable(table);
      return getTableViewForPlayer(table, playerId);
    }

    if (type === 'DROP') {
      if (table.turnPhase !== 'DRAW') throw new Error('Can only drop at the start of your turn before drawing');
      const dropInfo = getDropPenalty(table.mode, seat.hasDrawnThisHand);
      if (!dropInfo.allowed) throw new Error(dropInfo.reasonEn);
      seat.isDropped = true;
      seat.lastHandPoints = dropInfo.points;

      const remaining = table.seats.filter((s) => !s.isDropped && !s.isEliminated);
      if (remaining.length === 1) {
        const winner = remaining[0];
        settleHand(table, winner, [winner.hand]);
      } else {
        advanceToNextSeat(table);
      }
      broadcastTable(table);
      return getTableViewForPlayer(table, playerId);
    }

    if (type === 'SUBMIT_FINISH') {
      if (table.turnPhase !== 'DISCARD') throw new Error('Draw to 14 cards before finishing');
      const finishCardId = payload.finishCardId;
      const groupsIds = payload.groups;
      if (!finishCardId || !Array.isArray(groupsIds)) {
        throw new Error('finishCardId and groups are required');
      }
      const finishCard = seat.hand.find((c) => c.id === finishCardId);
      if (!finishCard) throw new Error('Finish card not in hand');

      const remainingHand = seat.hand.filter((c) => c.id !== finishCardId);
      const byId = new Map(remainingHand.map((c) => [c.id, c]));
      const groupCards = groupsIds.map((gIds) => gIds.map((id) => byId.get(id)).filter(Boolean));
      const validation = validateDeclaration(groupCards, table.wildRank, remainingHand);

      if (validation.isValid) {
        removeCardFromSeat(seat, finishCardId);
        table.openPile.push(finishCard);
        settleHand(table, seat, groupCards);
        broadcastTable(table);
        return getTableViewForPlayer(table, playerId);
      } else {
        // Invalid declaration -> 80 points penalty & dropped for this hand
        removeCardFromSeat(seat, finishCardId);
        table.openPile.push(finishCard);
        seat.wrongDeclaration = true;
        seat.isDropped = true;
        seat.lastHandPoints = 80;
        const remaining = table.seats.filter((s) => !s.isDropped && !s.isEliminated);
        if (remaining.length === 1) {
          const winner = remaining[0];
          settleHand(table, winner, [winner.hand], {
            [seat.playerId]: validation.reasonEn,
          });
        } else {
          advanceToNextSeat(table);
        }
        broadcastTable(table);
        return getTableViewForPlayer(table, playerId);
      }
    }

    throw new Error(`Unknown action type: ${type}`);
  }

  function getTableViewForPlayer(table, playerId) {
    const now = Date.now();
    const mySeat = table.seats.find((s) => s.playerId === playerId) || null;
    const isShowdown = table.status === 'HAND_RESULT' || table.status === 'MATCH_RESULT';

    return {
      tableId: table.id,
      mode: table.mode,
      maxSeats: table.maxSeats,
      stake: table.stake,
      dealNumber: table.dealNumber,
      totalDeals: table.totalDeals,
      dealSequenceId: table.dealSequenceId,
      status: table.status,
      serverTimeMs: now,
      countdownEndsAt: table.countdownEndsAt,
      countdownRemainingMs: table.countdownEndsAt ? Math.max(0, table.countdownEndsAt - now) : null,
      activeTurnSeatIndex: table.activeTurnSeatIndex,
      activePlayerId: table.seats[table.activeTurnSeatIndex]?.playerId || null,
      turnPhase: table.turnPhase,
      drawnFromOpenCardId: mySeat?.playerId === table.seats[table.activeTurnSeatIndex]?.playerId ? table.drawnFromOpenCardId : null,
      jokerIndicator: table.jokerIndicator,
      wildRank: table.wildRank,
      openPileTop: table.openPile.length > 0 ? table.openPile[table.openPile.length - 1] : null,
      openPileCount: table.openPile.length,
      closedStockCount: table.closedStock.length,
      seats: table.seats.map((s) => ({
        playerId: s.playerId,
        name: s.name,
        avatarId: s.avatarId,
        seatIndex: s.seatIndex,
        chips: s.chips,
        matchPoints: s.matchPoints,
        lastHandPoints: s.lastHandPoints,
        cardCount: s.hand.length,
        isDropped: s.isDropped,
        isEliminated: s.isEliminated,
        wrongDeclaration: s.wrongDeclaration,
        hasDrawnThisHand: s.hasDrawnThisHand,
        connected: s.connected,
      })),
      mySeatIndex: mySeat ? mySeat.seatIndex : -1,
      myHand: mySeat ? mySeat.hand : [],
      myArrangedGroups: mySeat ? mySeat.arrangedGroups : [],
      myLastDrawnCardId: mySeat ? mySeat.lastDrawnCardId : null,
      result: isShowdown ? table.lastResult : null,
    };
  }

  function broadcastTable(table) {
    for (const [socket, meta] of wsClients.entries()) {
      if (meta.tableId === table.id) {
        sendWsJson(socket, {
          event: 'TABLE_STATE',
          state: getTableViewForPlayer(table, meta.playerId),
        });
      }
    }
  }

  // Minimal RFC-6455 WebSocket helper
  function sendWsJson(socket, data) {
    if (socket.destroyed || !socket.writable) return;
    const payload = Buffer.from(JSON.stringify(data), 'utf8');
    const len = payload.length;
    let header;
    if (len < 126) {
      header = Buffer.alloc(2);
      header[0] = 0x81;
      header[1] = len;
    } else if (len < 65536) {
      header = Buffer.alloc(4);
      header[0] = 0x81;
      header[1] = 126;
      header.writeUInt16BE(len, 2);
    } else {
      header = Buffer.alloc(10);
      header[0] = 0x81;
      header[1] = 127;
      header.writeBigUInt64BE(BigInt(len), 2);
    }
    socket.write(Buffer.concat([header, payload]));
  }

  function parseWsFrames(buffer, onMessage, onClose) {
    let offset = 0;
    while (offset + 2 <= buffer.length) {
      const b1 = buffer[offset];
      const b2 = buffer[offset + 1];
      const opcode = b1 & 0x0f;
      const masked = (b2 & 0x80) !== 0;
      let len = b2 & 0x7f;
      let pos = offset + 2;

      if (len === 126) {
        if (pos + 2 > buffer.length) break;
        len = buffer.readUInt16BE(pos);
        pos += 2;
      } else if (len === 127) {
        if (pos + 8 > buffer.length) break;
        len = Number(buffer.readBigUInt64BE(pos));
        pos += 8;
      }

      const maskOffset = masked ? 4 : 0;
      if (pos + maskOffset + len > buffer.length) break;

      let payload = buffer.subarray(pos + maskOffset, pos + maskOffset + len);
      if (masked) {
        const mask = buffer.subarray(pos, pos + 4);
        const unmasked = Buffer.alloc(len);
        for (let i = 0; i < len; i++) {
          unmasked[i] = payload[i] ^ mask[i % 4];
        }
        payload = unmasked;
      }

      offset = pos + maskOffset + len;
      if (opcode === 0x8) {
        onClose();
        return buffer.subarray(offset);
      } else if (opcode === 0x9) {
        // Ping -> ignore or handled
      } else if (opcode === 0x1) {
        onMessage(payload.toString('utf8'));
      }
    }
    return buffer.subarray(offset);
  }

  function readJsonBody(req) {
    return new Promise((resolve, reject) => {
      let body = '';
      req.on('data', (chunk) => {
        body += chunk;
        if (body.length > 1_000_000) reject(new Error('Body too large'));
      });
      req.on('end', () => {
        try {
          resolve(body ? JSON.parse(body) : {});
        } catch (err) {
          reject(err);
        }
      });
      req.on('error', reject);
    });
  }

  function sendJson(res, status, data) {
    res.writeHead(status, {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
    });
    res.end(JSON.stringify(data));
  }

  const server = http.createServer(async (req, res) => {
    if (req.method === 'OPTIONS') {
      res.writeHead(204, {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
      });
      res.end();
      return;
    }

    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

    try {
      if (req.method === 'GET' && url.pathname === '/api/health') {
        return sendJson(res, 200, { ok: true, tables: tables.size, serverTimeMs: Date.now() });
      }

      if (req.method === 'POST' && url.pathname === '/api/tables/join') {
        const body = await readJsonBody(req);
        const joined = joinOrReconnect(body);
        return sendJson(res, 200, {
          reconnectToken: joined.profile.reconnectToken,
          reconnected: joined.reconnected,
          state: getTableViewForPlayer(joined.table, joined.profile.playerId),
        });
      }

      if (req.method === 'POST' && url.pathname === '/api/tables/leave') {
        const body = await readJsonBody(req);
        leaveTable(body.playerId);
        return sendJson(res, 200, { ok: true });
      }

      if (req.method === 'GET' && url.pathname.startsWith('/api/tables/')) {
        const parts = url.pathname.split('/').filter(Boolean);
        if (parts.length === 4 && parts[3] === 'state') {
          const tableId = parts[2];
          const playerId = url.searchParams.get('playerId');
          const table = tables.get(tableId);
          if (!table) return sendJson(res, 404, { error: 'Table not found' });
          return sendJson(res, 200, { state: getTableViewForPlayer(table, playerId) });
        }
      }

      if (req.method === 'POST' && url.pathname.startsWith('/api/tables/') && url.pathname.endsWith('/action')) {
        const parts = url.pathname.split('/').filter(Boolean);
        const tableId = parts[2];
        const body = await readJsonBody(req);
        const state = handleAction(body.playerId, { ...body, tableId });
        return sendJson(res, 200, { state });
      }

      // Static web client serving
      let relPath = url.pathname === '/' ? '/index.html' : url.pathname;
      const safePath = path.normalize(relPath).replace(/^(\.\.[/\\])+/, '');
      const filePath = path.join(PUBLIC_DIR, safePath);
      if (filePath.startsWith(PUBLIC_DIR) && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
        const ext = path.extname(filePath).toLowerCase();
        res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
        fs.createReadStream(filePath).pipe(res);
        return;
      }

      sendJson(res, 404, { error: 'Not found' });
    } catch (err) {
      sendJson(res, 400, { error: err.message || 'Bad request' });
    }
  });

  server.on('upgrade', (req, socket) => {
    const key = req.headers['sec-websocket-key'];
    if (!key) {
      socket.destroy();
      return;
    }
    const accept = crypto
      .createHash('sha1')
      .update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11')
      .digest('base64');

    socket.write(
      [
        'HTTP/1.1 101 Switching Protocols',
        'Upgrade: websocket',
        'Connection: Upgrade',
        `Sec-WebSocket-Accept: ${accept}`,
        '',
        '',
      ].join('\r\n')
    );

    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const playerId = url.searchParams.get('playerId') || '';
    const tableId = url.searchParams.get('tableId') || '';

    if (playerId && tableId && tables.has(tableId)) {
      const table = tables.get(tableId);
      const seat = table.seats.find((s) => s.playerId === playerId);
      if (seat) {
        seat.connected = true;
        wsClients.set(socket, { playerId, tableId });
        sendWsJson(socket, {
          event: 'TABLE_STATE',
          state: getTableViewForPlayer(table, playerId),
        });
        broadcastTable(table);
      }
    } else {
      wsClients.set(socket, { playerId, tableId });
    }

    let recvBuffer = Buffer.alloc(0);
    socket.on('data', (chunk) => {
      recvBuffer = Buffer.concat([recvBuffer, chunk]);
      recvBuffer = parseWsFrames(
        recvBuffer,
        (msgText) => {
          try {
            const msg = JSON.parse(msgText);
            if (msg.type === 'SUBSCRIBE') {
              wsClients.set(socket, { playerId: msg.playerId, tableId: msg.tableId });
              const t = tables.get(msg.tableId);
              if (t) {
                const s = t.seats.find((x) => x.playerId === msg.playerId);
                if (s) s.connected = true;
                sendWsJson(socket, {
                  event: 'TABLE_STATE',
                  state: getTableViewForPlayer(t, msg.playerId),
                });
                broadcastTable(t);
              }
            } else if (msg.type === 'PING') {
              sendWsJson(socket, { event: 'PONG', serverTimeMs: Date.now() });
            } else if (msg.type) {
              const meta = wsClients.get(socket);
              const pid = msg.playerId || meta?.playerId;
              const state = handleAction(pid, { ...msg, tableId: msg.tableId || meta?.tableId });
              sendWsJson(socket, { event: 'ACTION_OK', state });
            }
          } catch (err) {
            sendWsJson(socket, { event: 'ERROR', error: err.message });
          }
        },
        () => {
          socket.end();
        }
      );
    });

    socket.on('close', () => {
      const meta = wsClients.get(socket);
      wsClients.delete(socket);
      if (meta && meta.tableId && tables.has(meta.tableId)) {
        const table = tables.get(meta.tableId);
        const seat = table.seats.find((s) => s.playerId === meta.playerId);
        if (seat) {
          seat.connected = false;
          broadcastTable(table);
        }
      }
    });

    socket.on('error', () => {
      wsClients.delete(socket);
    });
  });

  return {
    server,
    tables,
    profiles,
    closeAll: () => {
      for (const table of tables.values()) {
        clearCountdown(table);
        clearTurnTimer(table);
      }
      for (const sock of wsClients.keys()) {
        sock.destroy();
      }
      server.close();
    },
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename)) {
  const port = Number(process.env.PORT || 8787);
  const { server } = createRummyServer();
  server.listen(port, '0.0.0.0', () => {
    console.log(`Velvet Rummy Den multiplayer backend running on http://0.0.0.0:${port}`);
  });
}
