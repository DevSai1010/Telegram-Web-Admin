package com.msma.tgpanel

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Optional "always-on connection". It does no work itself: being a foreground service keeps the process alive and
 * un-throttled, so [Realtime] (which lives in the process) keeps its WebSocket and new messages arrive at once.
 * Started/stopped by [App.setKeepConnected]; the user turns it on in Settings.
 */
class RealtimeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as App
        if (!app.prefs.loggedIn || !app.prefs.keepConnected) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, Notifier.SERVICE_ID, app.notifier.serviceNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {            // e.g. the system refuses a restart from the background
            stopSelf()
            return START_NOT_STICKY
        }
        app.realtime.start()
        return START_STICKY
    }
}
