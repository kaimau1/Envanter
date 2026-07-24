package com.envanter.app.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.envanter.app.MainActivity
import com.envanter.app.R
import com.envanter.app.data.Repository
import com.envanter.app.data.Settings
import com.envanter.app.data.Urgency
import kotlinx.coroutines.flow.first

/** Günde 2 kez envanteri tarar; kırmızı/süresi geçmiş ürünler için bildirim atar. */
class ExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Settings.notifEnabled(ctx).first()) return Result.success()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return Result.success()

        val urgent = Repository.items().filter { it.urgency == Urgency.RED || it.urgency == Urgency.EXPIRED }
        if (urgent.isEmpty()) return Result.success()

        val expired = urgent.count { it.urgency == Urgency.EXPIRED }
        val red = urgent.size - expired
        val title = "Dikkat: ${urgent.size} ürünün tarihi kritik!"
        val text = buildString {
            if (expired > 0) append("$expired ürünün tarihi geçti. ")
            if (red > 0) append("$red ürünün tarihi çok yakın. ")
            append(urgent.take(3).joinToString(", ") { it.name })
            if (urgent.size > 3) append("…")
        }

        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(1, notif)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "expiry_alerts"
    }
}
