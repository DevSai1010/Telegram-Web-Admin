package com.msma.tgpanel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.msma.tgpanel.core.Backoff
import com.msma.tgpanel.data.Api
import com.msma.tgpanel.data.AppJson
import com.msma.tgpanel.data.Prefs
import com.msma.tgpanel.data.Repo
import com.msma.tgpanel.data.WsFrame
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** What the connection bar shows (same states the web panel and Telegram use). */
enum class ConnState { Connecting, WaitingNetwork, Updating, Connected }

/**
 * Live channel to the panel (docs/API.md §3): WebSocket "dirty" hints → [Repo.syncNow].
 * Everything here runs on [scope] (the main thread), so there are no races between the
 * OkHttp callbacks, the network callback and the retry timer.
 */
class Realtime(
    ctx: Context,
    private val prefs: Prefs,
    private val api: Api,
    private val repo: Repo,
    private val scope: CoroutineScope,
) {
    val state = MutableStateFlow(ConnState.Connecting)

    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var socket: WebSocket? = null
    private var retryJob: Job? = null
    private var syncJob: Job? = null
    private var safetyJob: Job? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var wanted = false
    private var online = true
    private var attempt = 0
    private var awaitingFirstSync = false
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        if (!prefs.loggedIn || wanted) return
        wanted = true
        attempt = 0
        online = isOnline()
        registerNetwork()
        syncJob = scope.launch {
            for (ignored in syncRequests) {
                val ok = repo.syncNow()
                if (ok) {
                    if (awaitingFirstSync && socket != null) { awaitingFirstSync = false; state.value = ConnState.Connected }
                } else if (wanted) {
                    delay(3000)                       // transient failure: try the same sync again shortly
                    syncRequests.trySend(Unit)
                }
            }
        }
        // if the socket cannot be established (proxy hiccup, captive portal…) the app still catches up by plain sync
        safetyJob = scope.launch {
            while (true) {
                delay(30_000)
                if (wanted && online && state.value != ConnState.Connected) syncRequests.trySend(Unit)
            }
        }
        open()
    }

    fun stop() {
        wanted = false
        retryJob?.cancel(); syncJob?.cancel(); safetyJob?.cancel()
        unregisterNetwork()
        socket?.cancel(); socket = null
        awaitingFirstSync = false
        state.value = ConnState.Connecting
    }

    private fun open() {
        if (!wanted) return
        if (!online) { state.value = ConnState.WaitingNetwork; return }
        state.value = ConnState.Connecting
        val req = try { Request.Builder().url(api.wsUrl()).build() } catch (e: IllegalArgumentException) { return }
        val ws = api.http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val auth = JsonObject(mapOf("t" to JsonPrimitive("auth"), "token" to JsonPrimitive(prefs.token)))
                webSocket.send(auth.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) { scope.launch { onFrame(webSocket, text) } }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { scope.launch { onGone(webSocket, code) } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { scope.launch { onGone(webSocket, -1) } }
        })
        socket = ws
    }

    private fun onFrame(ws: WebSocket, text: String) {
        if (ws !== socket) return                                  // a socket we already replaced
        val f = try { AppJson.decodeFromString<WsFrame>(text) } catch (e: Exception) { return }
        when (f.t) {
            "hello" -> { attempt = 0; awaitingFirstSync = true; state.value = ConnState.Updating; syncRequests.trySend(Unit) }
            "dirty" -> syncRequests.trySend(Unit)
            "ping" -> ws.send("{\"t\":\"pong\"}")
            "unauthorized" -> { wanted = false; api.authLost.tryEmit(Unit) }
        }
    }

    private fun onGone(ws: WebSocket, code: Int) {
        if (ws !== socket) return
        socket = null
        if (code == 4401) { wanted = false; api.authLost.tryEmit(Unit); return }
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!wanted) return
        state.value = if (online) ConnState.Connecting else ConnState.WaitingNetwork
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(Backoff.delayMs(attempt++, Random.nextDouble()))
            open()
        }
    }

    // ---- network awareness: reconnect the moment a network is back, show "waiting" while there is none ----
    private fun isOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetwork() {
        unregisterNetwork()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    online = true
                    if (wanted && socket == null) { retryJob?.cancel(); attempt = 0; open() }
                }
            }
            override fun onLost(network: Network) {
                scope.launch {
                    online = false
                    if (wanted) {
                        retryJob?.cancel()
                        socket?.cancel(); socket = null
                        state.value = ConnState.WaitingNetwork
                    }
                }
            }
        }
        try { cm.registerDefaultNetworkCallback(cb); netCallback = cb } catch (e: Exception) { /* no callback → retries still work */ }
    }

    private fun unregisterNetwork() {
        netCallback?.let { try { cm.unregisterNetworkCallback(it) } catch (e: Exception) { } }
        netCallback = null
    }
}
