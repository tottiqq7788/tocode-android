package app.tocode.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

const val SESSION_FINISHED_CHANNEL = "session-finished"

fun showFinishedNotification(context: Context, session: RunningSession) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val app = context.applicationContext
    val manager = NotificationManagerCompat.from(app)
    if (Build.VERSION.SDK_INT >= 26) {
        val channel = NotificationChannel(SESSION_FINISHED_CHANNEL, "会话完成", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }
    val open = session.toSessionOpen()
    val intent = Intent(app, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putSessionOpen(open)
    }
    val pending = PendingIntent.getActivity(
        app,
        notificationId(session),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(app, SESSION_FINISHED_CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_session)
        .setContentTitle(session.title.ifBlank { "未命名会话" })
        .setContentText("已回复")
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()
    runCatching { manager.notify(notificationId(session), notification) }
}

fun Intent.putSessionOpen(open: SessionOpen) {
    putExtra(EXTRA_LINK_ID, open.linkId)
    putExtra(EXTRA_PROFILE, open.profile)
    putExtra(EXTRA_PROJECT_ID, open.projectId)
    putExtra(EXTRA_PROJECT_NAME, open.projectName)
    putExtra(EXTRA_SESSION_ID, open.sessionId)
    putExtra(EXTRA_SESSION_TITLE, open.title)
}

private fun RunningSession.toSessionOpen(): SessionOpen =
    SessionOpen(linkId, profile, projectId, projectName, sessionId, title)

private fun notificationId(session: RunningSession): Int = (session.linkId + "\u0000" + session.sessionId).hashCode()
