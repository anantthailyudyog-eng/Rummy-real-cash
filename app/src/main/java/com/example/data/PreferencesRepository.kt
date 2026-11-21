package com.example.data

import android.content.Context
import com.example.i18n.AppLanguage
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

data class GuestProfile(
    val playerId: String,
    val reconnectToken: String,
    val name: String,
    val avatarId: Int,
    val chips: Int,
    val lastBonusDate: String
)

class PreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("velvet_rummy_den_prefs", Context.MODE_PRIVATE)

    fun getOrCreateGuestProfile(): GuestProfile {
        val playerId = prefs.getString("player_id", null) ?: run {
            val generated = "andr_${UUID.randomUUID().toString().take(8)}"
            prefs.edit().putString("player_id", generated).apply()
            generated
        }
        val token = prefs.getString("reconnect_token", "") ?: ""
        val name = prefs.getString("player_name", null) ?: run {
            val generated = "Velvet_${playerId.takeLast(4).uppercase(Locale.US)}"
            prefs.edit().putString("player_name", generated).apply()
            generated
        }
        val avatarId = if (prefs.contains("avatar_id")) {
            prefs.getInt("avatar_id", 0)
        } else {
            val picked = Random.nextInt(0, 6)
            prefs.edit().putInt("avatar_id", picked).apply()
            picked
        }
        val chips = if (prefs.contains("chips")) {
            prefs.getInt("chips", 5000)
        } else {
            prefs.edit().putInt("chips", 5000).apply()
            5000
        }
        val lastBonusDate = prefs.getString("last_bonus_date", "") ?: ""
        return GuestProfile(playerId, token, name, avatarId, chips, lastBonusDate)
    }

    fun saveReconnectToken(token: String) {
        prefs.edit().putString("reconnect_token", token).apply()
    }

    fun saveChips(chips: Int) {
        prefs.edit().putInt("chips", chips).apply()
    }

    fun canClaimDailyBonus(): Boolean {
        val today = todayIso()
        return prefs.getString("last_bonus_date", "") != today
    }

    fun claimDailyBonus(): GuestProfile {
        val current = getOrCreateGuestProfile()
        val today = todayIso()
        if (current.lastBonusDate == today) return current
        val nextChips = current.chips + 1000
        prefs.edit()
            .putInt("chips", nextChips)
            .putString("last_bonus_date", today)
            .apply()
        return current.copy(chips = nextChips, lastBonusDate = today)
    }

    fun getLanguage(): AppLanguage = AppLanguage.fromCode(prefs.getString("lang", "en") ?: "en")
    fun setLanguage(lang: AppLanguage) = prefs.edit().putString("lang", lang.code).apply()

    fun isMuted(): Boolean = prefs.getBoolean("muted", false)
    fun setMuted(muted: Boolean) = prefs.edit().putBoolean("muted", muted).apply()

    fun isVoiceEnabled(): Boolean = prefs.getBoolean("voice_enabled", true)
    fun setVoiceEnabled(enabled: Boolean) = prefs.edit().putBoolean("voice_enabled", enabled).apply()

    fun saveArrangement(tableId: String, dealNumber: Int, groups: List<List<String>>) {
        val arr = JSONArray()
        groups.forEach { g ->
            val inner = JSONArray()
            g.forEach { inner.put(it) }
            arr.put(inner)
        }
        prefs.edit().putString("arr_${tableId}_$dealNumber", arr.toString()).apply()
    }

    fun loadArrangement(tableId: String, dealNumber: Int): List<List<String>>? {
        val raw = prefs.getString("arr_${tableId}_$dealNumber", null) ?: return null
        return try {
            val outer = JSONArray(raw)
            val result = mutableListOf<List<String>>()
            for (i in 0 until outer.length()) {
                val inner = outer.getJSONArray(i)
                val g = mutableListOf<String>()
                for (j in 0 until inner.length()) g.add(inner.getString(j))
                if (g.isNotEmpty()) result.add(g)
            }
            result
        } catch (_: Throwable) {
            null
        }
    }

    private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
