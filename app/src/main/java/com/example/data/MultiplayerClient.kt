package com.example.data

import com.example.BuildConfig
import com.example.domain.Card
import com.example.domain.ConnectionStatus
import com.example.domain.GameMode
import com.example.domain.HandResultSummary
import com.example.domain.PlayerScoreRow
import com.example.domain.SeatInfo
import com.example.domain.Suit
import com.example.domain.TableSnapshot
import com.example.domain.TableStatus
import com.example.domain.TurnPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MultiplayerClient(
    private val baseUrl: String = BuildConfig.MULTIPLAYER_BASE_URL.trimEnd('/')
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private var webSocket: WebSocket? = null

    data class JoinResult(
        val reconnectToken: String,
        val reconnected: Boolean,
        val snapshot: TableSnapshot
    )

    suspend fun joinOrReconnect(
        profile: GuestProfile,
        mode: GameMode,
        maxSeats: Int,
        stake: Int = 100
    ): Result<JoinResult> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("playerId", profile.playerId)
                if (profile.reconnectToken.isNotBlank()) put("reconnectToken", profile.reconnectToken)
                put("name", profile.name)
                put("avatarId", profile.avatarId)
                put("chips", profile.chips)
                put("mode", mode.apiCode)
                put("maxSeats", maxSeats)
                put("stake", stake)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/tables/join")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = JSONObject(text)
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(json.optString("error", "Join failed")))
                }
                val token = json.optString("reconnectToken", profile.reconnectToken)
                val reconnected = json.optBoolean("reconnected", false)
                val state = parseTableSnapshot(json.getJSONObject("state"))
                Result.success(JoinResult(token, reconnected, state))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun sendAction(tableId: String, playerId: String, body: JSONObject): Result<TableSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                body.put("playerId", playerId)
                val req = Request.Builder()
                    .url("$baseUrl/api/tables/$tableId/action")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val json = JSONObject(text)
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException(json.optString("error", "Action failed")))
                    }
                    Result.success(parseTableSnapshot(json.getJSONObject("state")))
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    suspend fun leaveTable(playerId: String) = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("playerId", playerId)
            val req = Request.Builder()
                .url("$baseUrl/api/tables/leave")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().close()
        } catch (_: Throwable) {
        }
    }

    fun connectWebSocket(
        tableId: String,
        playerId: String,
        onState: (TableSnapshot) -> Unit,
        onStatus: (ConnectionStatus) -> Unit,
        onError: (String) -> Unit
    ) {
        disconnectWebSocket()
        val wsUrl = baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
            "/ws?playerId=$playerId&tableId=$tableId"

        val req = Request.Builder().url(wsUrl).build()
        onStatus(ConnectionStatus.CONNECTING)
        webSocket = http.newWebSocket(
            req,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onStatus(ConnectionStatus.CONNECTED)
                    val sub = JSONObject()
                        .put("type", "SUBSCRIBE")
                        .put("playerId", playerId)
                        .put("tableId", tableId)
                    webSocket.send(sub.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = JSONObject(text)
                        val ev = msg.optString("event")
                        if ((ev == "TABLE_STATE" || ev == "ACTION_OK") && msg.has("state")) {
                            onState(parseTableSnapshot(msg.getJSONObject("state")))
                        } else if (ev == "ERROR") {
                            onError(msg.optString("error", "Server error"))
                        }
                    } catch (_: Throwable) {
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onStatus(ConnectionStatus.RECONNECTING)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onStatus(ConnectionStatus.OFFLINE)
                }
            }
        )
    }

    fun sendArrangementWs(tableId: String, playerId: String, groups: List<List<String>>) {
        val ws = webSocket ?: return
        val arr = JSONArray()
        groups.forEach { g ->
            val inner = JSONArray()
            g.forEach { inner.put(it) }
            arr.put(inner)
        }
        val msg = JSONObject()
            .put("type", "SAVE_ARRANGEMENT")
            .put("tableId", tableId)
            .put("playerId", playerId)
            .put("groups", arr)
        ws.send(msg.toString())
    }

    fun disconnectWebSocket() {
        webSocket?.close(1000, "Leaving table")
        webSocket = null
    }

    companion object {
        fun parseCard(obj: JSONObject): Card = Card(
            id = obj.getString("id"),
            deck = obj.optInt("deck", 0),
            suit = Suit.fromCode(obj.optString("suit", "S")),
            rank = obj.optInt("rank", 1),
            isPrintedJoker = obj.optBoolean("isPrintedJoker", false)
        )

        fun parseTableSnapshot(obj: JSONObject): TableSnapshot {
            val seatsArr = obj.optJSONArray("seats") ?: JSONArray()
            val seats = (0 until seatsArr.length()).map { i ->
                val s = seatsArr.getJSONObject(i)
                SeatInfo(
                    playerId = s.getString("playerId"),
                    name = s.getString("name"),
                    avatarId = s.optInt("avatarId", 0),
                    seatIndex = s.optInt("seatIndex", i),
                    chips = s.optInt("chips", 0),
                    matchPoints = s.optInt("matchPoints", 0),
                    lastHandPoints = s.optInt("lastHandPoints", 0),
                    cardCount = s.optInt("cardCount", 0),
                    isDropped = s.optBoolean("isDropped", false),
                    isEliminated = s.optBoolean("isEliminated", false),
                    wrongDeclaration = s.optBoolean("wrongDeclaration", false),
                    hasDrawnThisHand = s.optBoolean("hasDrawnThisHand", false),
                    connected = s.optBoolean("connected", true)
                )
            }

            val handArr = obj.optJSONArray("myHand") ?: JSONArray()
            val myHand = (0 until handArr.length()).map { parseCard(handArr.getJSONObject(it)) }

            val arrOuter = obj.optJSONArray("myArrangedGroups") ?: JSONArray()
            val arranged = (0 until arrOuter.length()).map { i ->
                val inner = arrOuter.getJSONArray(i)
                (0 until inner.length()).map { j -> inner.getString(j) }
            }

            val remainingMs = if (obj.isNull("countdownRemainingMs")) null else obj.optLong("countdownRemainingMs")
            val countdownEnd = remainingMs?.let { System.currentTimeMillis() + it }

            val resObj = obj.optJSONObject("result")
            val result = resObj?.let { r ->
                val wgArr = r.optJSONArray("winningGroups") ?: JSONArray()
                val wg = (0 until wgArr.length()).map { i ->
                    val inner = wgArr.getJSONArray(i)
                    (0 until inner.length()).map { j -> parseCard(inner.getJSONObject(j)) }
                }
                val hsArr = r.optJSONArray("handScores") ?: JSONArray()
                val hs = (0 until hsArr.length()).map { i ->
                    val row = hsArr.getJSONObject(i)
                    PlayerScoreRow(
                        playerId = row.getString("playerId"),
                        name = row.getString("name"),
                        avatarId = row.optInt("avatarId", 0),
                        handPoints = row.optInt("handPoints", 0),
                        matchPoints = row.optInt("matchPoints", 0),
                        isWinner = row.optBoolean("isWinner", false),
                        isDropped = row.optBoolean("isDropped", false),
                        wrongDeclaration = row.optBoolean("wrongDeclaration", false)
                    )
                }
                HandResultSummary(
                    handWinnerId = r.getString("handWinnerId"),
                    handWinnerName = r.getString("handWinnerName"),
                    matchWinnerId = if (r.isNull("matchWinnerId")) null else r.optString("matchWinnerId"),
                    matchWinnerName = if (r.isNull("matchWinnerName")) null else r.optString("matchWinnerName"),
                    matchFinished = r.optBoolean("matchFinished", false),
                    dealNumber = r.optInt("dealNumber", 1),
                    totalDeals = r.optInt("totalDeals", 1),
                    winningGroups = wg,
                    handScores = hs,
                    grossReward = r.optInt("grossReward", 0),
                    commission = r.optInt("commission", 0),
                    netReward = r.optInt("netReward", 0)
                )
            }

            return TableSnapshot(
                tableId = obj.getString("tableId"),
                mode = GameMode.fromCode(obj.optString("mode", "POINTS")),
                maxSeats = obj.optInt("maxSeats", 4),
                stake = obj.optInt("stake", 100),
                dealNumber = obj.optInt("dealNumber", 0),
                totalDeals = obj.optInt("totalDeals", 1),
                dealSequenceId = obj.optInt("dealSequenceId", 0),
                status = TableStatus.fromCode(obj.optString("status", "WAITING")),
                countdownEndsAtMs = countdownEnd,
                activeTurnSeatIndex = obj.optInt("activeTurnSeatIndex", 0),
                turnPhase = TurnPhase.fromCode(obj.optString("turnPhase", "DRAW")),
                drawnFromOpenCardId = if (obj.isNull("drawnFromOpenCardId")) null else obj.optString("drawnFromOpenCardId"),
                jokerIndicator = obj.optJSONObject("jokerIndicator")?.let(::parseCard),
                wildRank = obj.optInt("wildRank", 1),
                openPileTop = obj.optJSONObject("openPileTop")?.let(::parseCard),
                openPileCount = obj.optInt("openPileCount", 0),
                closedStockCount = obj.optInt("closedStockCount", 0),
                seats = seats,
                mySeatIndex = obj.optInt("mySeatIndex", 0),
                myHand = myHand,
                myArrangedGroups = arranged,
                myLastDrawnCardId = if (obj.isNull("myLastDrawnCardId")) null else obj.optString("myLastDrawnCardId"),
                result = result
            )
        }
    }
}
