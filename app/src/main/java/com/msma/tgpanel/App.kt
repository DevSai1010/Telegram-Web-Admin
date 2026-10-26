package com.msma.tgpanel

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.msma.tgpanel.data.Api
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

    val loggedIn = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = Api(prefs)
        db = PanelDb.create(this)
        repo = Repo(prefs, api, db)
        realtime = Realtime(this, prefs, api, repo, appScope)
        loggedIn.value = prefs.loggedIn

        // the server says our token is dead (revoked from another device, password changed…)
        appScope.launch { api.authLost.collect { if (prefs.loggedIn) logout(callServer = false) } }

        // live connection only while the app is visible (a background service comes in a later version)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { if (prefs.loggedIn) realtime.start() }
            override fun onStop(owner: LifecycleOwner) { realtime.stop() }
        })
    }

    fun onSignedIn(token: String, username: String, isOwner: Boolean) {
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
            realtime.stop()
            prefs.clearSession()
            repo.clearAll()
            loggedIn.value = false
        }
    }
}
