package com.msma.tgpanel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.msma.tgpanel.data.Api
import com.msma.tgpanel.data.MediaHolder
import com.msma.tgpanel.data.PanelDb
import com.msma.tgpanel.data.Prefs
import com.msma.tgpanel.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Process-wide singletons (small app — a hand-rolled container is plenty). */
class App : Application() {
    /** Main-thread scope: UI-adjacent coordination; real work hops to IO inside Api / Room. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var prefs: Prefs
    lateinit var api: Api
    lateinit var db: PanelDb
    lateinit var repo: Repo
    lateinit var realtime: Realtime
    lateinit var notifier: Notifier
    lateinit var media: MediaHolder

    val loggedIn = MutableStateFlow(false)

    /** Set by a tapped notification; the navigation host opens that chat and clears it. */
    val openChatRequest = MutableStateFlow<String?>(null)

    private val visible: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = Api(prefs)
        db = PanelDb.create(this)
        repo = Repo(prefs, api, db)
        realtime = Realtime(this, prefs, api, repo, appScope)
        notifier = Notifier(this)
        media = MediaHolder(this)
        loggedIn.value = prefs.loggedIn

        // the server says our token is dead (revoked from another device, password changed…)
        appScope.launch { api.authLost.collect { if (prefs.loggedIn) logout(callServer = false) } }

        // a new customer message arrived while the app is not on screen → notification
        appScope.launch { repo.incoming.collect { chat -> if (!visible) notifier.showMessage(chat) } }
        // opening a chat clears its notification
        appScope.launch { repo.openChatId.collect { id -> if (id != null) notifier.cancel(id) } }

        // live connection while the app is visible; in the background only if the user turned on "always-on"
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { if (prefs.loggedIn) realtime.start() }
            override fun onStop(owner: LifecycleOwner) { if (!prefs.keepConnected) realtime.stop() }
        })

        if (prefs.loggedIn && prefs.keepConnected) startService()     // the process was restarted (reboot / killed): restore the service
    }

    fun onSignedIn(token: String, username: String, isOwner: Boolean) {
        media.release()                                   // a player built for the previous session would carry its token
        prefs.token = token
        prefs.username = username
        prefs.isOwner = isOwner
        prefs.cursor = ""
        prefs.chatsEtag = ""
        loggedIn.value = true
        realtime.start()
    }

    fun logout(callServer: Boolean = true) {
        appScope.launch {
            if (callServer) runCatching { api.logout() }
            stopService()
            realtime.stop()
            media.release()
            prefs.clearSession()
            repo.clearAll()
            loggedIn.value = false
        }
    }

    /** The "always-on connection" switch in Settings. */
    fun setKeepConnected(on: Boolean) {
        prefs.keepConnected = on
        if (on) startService() else { stopService(); if (!visible) realtime.stop() }
    }

    private fun startService() {
        try { ContextCompat.startForegroundService(this, Intent(this, RealtimeService::class.java)) } catch (e: Exception) { /* not allowed right now */ }
    }

    private fun stopService() {
        stopService(Intent(this, RealtimeService::class.java))
    }
}
