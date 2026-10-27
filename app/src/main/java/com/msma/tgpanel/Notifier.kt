package com.msma.tgpanel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.msma.tgpanel.data.ChatEntity

/** Message notifications and the persistent notification of the optional always-on service. */
class Notifier(private val ctx: Context) {
    companion object {
        const val CH_MESSAGES = "messages"
        const val CH_SERVICE = "service"
        const val SERVICE_ID = 1
        const val EXTRA_CHAT = "chat_id"
    }

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, ctx.getString(R.string.ch_messages), NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CH_SERVICE, ctx.getString(R.string.ch_service), NotificationManager.IMPORTANCE_MIN))
        }
    }

    private fun openApp(chatId: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (chatId != null) putExtra(EXTRA_CHAT, chatId)
        }
        return PendingIntent.getActivity(ctx, (chatId ?: "").hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun showMessage(chat: ChatEntity) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val text = (if (chat.lastSender.isNotEmpty()) chat.lastSender + ": " else "") + chat.lastText
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(chat.title.ifEmpty { chat.id })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(chat.id))
            .build()
        try { nm.notify(chat.id.hashCode(), n) } catch (e: SecurityException) { /* permission revoked meanwhile */ }
    }

    fun cancel(chatId: String) = NotificationManagerCompat.from(ctx).cancel(chatId.hashCode())

    fun serviceNotification(): Notification =
        NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.service_title))
            .setContentText(ctx.getString(R.string.service_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(null))
            .build()
}
