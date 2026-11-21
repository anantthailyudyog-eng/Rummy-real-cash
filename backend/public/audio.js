const RANK_WORDS_EN = {
  0: 'Printed Joker',
  1: 'ace',
  2: 'two',
  3: 'three',
  4: 'four',
  5: 'five',
  6: 'six',
  7: 'seven',
  8: 'eight',
  9: 'nine',
  10: 'ten',
  11: 'jack',
  12: 'queen',
  13: 'king',
};

const RANK_WORDS_HI = {
  0: 'प्रिंटेड जोकर',
  1: 'इक्का',
  2: 'दो',
  3: 'तीन',
  4: 'चार',
  5: 'पाँच',
  6: 'छह',
  7: 'सात',
  8: 'आठ',
  9: 'नौ',
  10: 'दस',
  11: 'गुलाम',
  12: 'बेगम',
  13: 'बादशाह',
};

const SUIT_INFO_EN = {
  S: { color: 'Black', name: 'spades' },
  C: { color: 'Black', name: 'clubs' },
  H: { color: 'Red', name: 'hearts' },
  D: { color: 'Red', name: 'diamonds' },
};

const SUIT_INFO_HI = {
  S: { color: 'काला', name: 'हुकुम' },
  C: { color: 'काला', name: 'चिड़ी' },
  H: { color: 'लाल', name: 'पान' },
  D: { color: 'लाल', name: 'ईंट' },
};

export class RummyAudio {
  constructor() {
    this.ctx = null;
    this.muted = false;
    this.spokenCards = true;
    this.unlocked = false;
  }

  unlock() {
    this.unlocked = true;
    if (!this.ctx && typeof window !== 'undefined' && (window.AudioContext || window.webkitAudioContext)) {
      const AC = window.AudioContext || window.webkitAudioContext;
      this.ctx = new AC();
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume().catch(() => {});
    }
  }

  tone(freq, duration = 0.08, type = 'triangle', gainVal = 0.12, delay = 0) {
    if (this.muted || !this.unlocked || !this.ctx) return;
    const t = this.ctx.currentTime + delay;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, t);
    gain.gain.setValueAtTime(0.001, t);
    gain.gain.exponentialRampToValueAtTime(gainVal, t + 0.012);
    gain.gain.exponentialRampToValueAtTime(0.0001, t + duration);
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start(t);
    osc.stop(t + duration + 0.02);
  }

  playSeat() {
    this.tone(523.25, 0.09, 'sine', 0.1);
    this.tone(659.25, 0.12, 'sine', 0.1, 0.07);
  }

  playDeal() {
    for (let i = 0; i < 4; i++) {
      this.tone(360 + i * 45, 0.05, 'triangle', 0.07, i * 0.055);
    }
  }

  playDraw() {
    this.tone(480, 0.06, 'triangle', 0.11);
    this.tone(640, 0.07, 'sine', 0.09, 0.04);
  }

  playDiscard() {
    this.tone(300, 0.07, 'sine', 0.12);
    this.tone(210, 0.08, 'triangle', 0.08, 0.03);
  }

  playError() {
    this.tone(190, 0.15, 'sawtooth', 0.08);
  }

  playWinCheer() {
    const notes = [523.25, 659.25, 783.99, 1046.5];
    notes.forEach((n, idx) => this.tone(n, 0.22, 'triangle', 0.13, idx * 0.1));
  }

  cancelSpeech() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
  }

  speakCard(card, lang = 'en') {
    if (this.muted || !this.spokenCards || !this.unlocked) return;
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();
    let text = '';
    if (card.isPrintedJoker || card.rank === 0) {
      text = lang === 'hi' ? RANK_WORDS_HI[0] : RANK_WORDS_EN[0];
    } else if (lang === 'hi') {
      const s = SUIT_INFO_HI[card.suit] || { color: '', name: '' };
      text = `${s.color}, ${s.name} का ${RANK_WORDS_HI[card.rank]}`;
    } else {
      const s = SUIT_INFO_EN[card.suit] || { color: '', name: '' };
      text = `${s.color}, ${RANK_WORDS_EN[card.rank]} of ${s.name}`;
    }
    const utter = new SpeechSynthesisUtterance(text);
    utter.lang = lang === 'hi' ? 'hi-IN' : 'en-US';
    utter.rate = 1.05;
    window.speechSynthesis.speak(utter);
  }
}
