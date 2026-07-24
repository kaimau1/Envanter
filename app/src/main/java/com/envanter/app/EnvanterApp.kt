package com.envanter.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.envanter.app.data.Repository
import com.envanter.app.notify.ExpiryWorker
import java.util.concurrent.TimeUnit

class EnvanterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                ExpiryWorker.CHANNEL_ID,
                "Son Kullanma Uyarıları",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "expiry-check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ExpiryWorker>(12, TimeUnit.HOURS).build()
        )
    }
}
