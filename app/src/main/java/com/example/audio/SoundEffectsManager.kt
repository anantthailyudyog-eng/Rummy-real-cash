package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import com.example.domain.Card
import com.example.domain.Suit
import com.example.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SoundEffectsManager(context: Context) : TextToSpeech.OnInitListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    @Volatile
    var hasUserInteracted = false

    @Volatile
    var isMuted = false

    @Volatile
    var isVoiceEnabled = true

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Throwable) {
            ttsReady = false
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
    }

    fun markInteracted() {
        hasUserInteracted = true
    }

    fun playSeat() = playChime(doubleArrayOf(523.25, 659.25), 90)
    fun playDeal() = playChime(doubleArrayOf(350.0, 400.0, 450.0, 500.0), 55)
    fun playDraw() = playChime(doubleArrayOf(480.0, 640.0), 70)
    fun playDiscard() = playChime(doubleArrayOf(300.0, 220.0), 75)
    fun playError() = playChime(doubleArrayOf(185.0, 155.0), 120)
    fun playWinCheer() = playChime(doubleArrayOf(523.25, 659.25, 783.99, 1046.5), 160)

    fun cancelSpeech() {
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
    }

    fun speakCard(card: Card, lang: AppLanguage) {
        if (!hasUserInteracted || isMuted || !isVoiceEnabled || !ttsReady) return
        val spoken = formatCardSpeech(card, lang)
        try {
            val targetLocale = if (lang == AppLanguage.HI) Locale.forLanguageTag("hi-IN") else Locale.US
            tts?.language = targetLocale
            tts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "card_${card.id}")
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {
        }
    }

    private fun playChime(frequencies: DoubleArray, noteMs: Int) {
        if (!hasUserInteracted || isMuted) return
        scope.launch {
            try {
                val sampleRate = 22050
                val samplesPerNote = (sampleRate * noteMs) / 1000
                val totalSamples = samplesPerNote * frequencies.size
                val pcm = ShortArray(totalSamples)

                for (n in frequencies.indices) {
                    val freq = frequencies[n]
                    val offset = n * samplesPerNote
                    for (i in 0 until samplesPerNote) {
                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 18.0) * (1.0 - exp(-t * 300.0))
                        val wave = sin(2.0 * PI * freq * t) * env
                        pcm[offset + i] = (wave * 9500).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                kotlinx.coroutines.delay((noteMs * frequencies.size + 80).toLong())
                track.stop()
                track.release()
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        fun formatCardSpeech(card: Card, lang: AppLanguage): String {
            if (card.isPrintedJoker || card.rank == 0) {
                return if (lang == AppLanguage.HI) "प्रिंटेड जोकर" else "Printed Joker"
            }
            if (lang == AppLanguage.HI) {
                val rankHi = when (card.rank) {
                    1 -> "इक्का"
                    2 -> "दो"
                    3 -> "तीन"
                    4 -> "चार"
                    5 -> "पाँच"
                    6 -> "छह"
                    7 -> "सात"
                    8 -> "आठ"
                    9 -> "नौ"
                    10 -> "दस"
                    11 -> "गुलाम"
                    12 -> "बेगम"
                    13 -> "बादशाह"
                    else -> card.rank.toString()
                }
                val (colorHi, suitHi) = when (card.suit) {
                    Suit.SPADES -> "काला" to "हुकुम"
                    Suit.CLUBS -> "काला" to "चिड़ी"
                    Suit.HEARTS -> "लाल" to "पान"
                    Suit.DIAMONDS -> "लाल" to "ईंट"
                    Suit.JOKER -> "" to "जोकर"
                }
                return "$colorHi, $suitHi का $rankHi"
            }
            val rankEn = when (card.rank) {
                1 -> "ace"
                2 -> "two"
                3 -> "three"
                4 -> "four"
                5 -> "five"
                6 -> "six"
                7 -> "seven"
                8 -> "eight"
                9 -> "nine"
                10 -> "ten"
                11 -> "jack"
                12 -> "queen"
                13 -> "king"
                else -> card.rank.toString()
            }
            val (colorEn, suitEn) = when (card.suit) {
                Suit.SPADES -> "Black" to "spades"
                Suit.CLUBS -> "Black" to "clubs"
                Suit.HEARTS -> "Red" to "hearts"
                Suit.DIAMONDS -> "Red" to "diamonds"
                Suit.JOKER -> "" to "joker"
            }
            return "$colorEn, $rankEn of $suitEn"
        }
    }
}
