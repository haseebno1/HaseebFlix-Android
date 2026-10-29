package com.haseebflix.app.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.haseebflix.app.R
import com.haseebflix.app.data.local.AppDatabase
import com.haseebflix.app.data.local.NotificationEntity
import com.haseebflix.app.data.repo.MediaRepository
import com.haseebflix.app.data.settings.SettingsRepository
import com.haseebflix.app.domain.MediaType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object Notifier {
    private const val CHANNEL = "episodes"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "New episodes & premieres", NotificationManager.IMPORTANCE_DEFAULT)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    @SuppressLint("MissingPermission")
    fun notify(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pi = PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_logo).setContentTitle(title).setContentText(text)
            .setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val media: MediaRepository,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()
        val lib = db.libraryDao()
        val nd = db.notificationDao()
        val now = System.currentTimeMillis()

        if (s.newEpisodeAlerts) {
            for (f in lib.follows()) {
                media.refreshDetail(MediaType.TV, f.showId, force = true)
                val d = media.detail(MediaType.TV, f.showId).first() ?: continue
                val last = d.lastEpisode ?: continue
                val key = "S${last.season}E${last.number}"
                if (f.lastNotified == null) { lib.setLastNotified(f.showId, key); continue }  // baseline, no alert
                if (f.lastNotified != key) {
                    lib.setLastNotified(f.showId, key)
                    nd.insert(
                        NotificationEntity(
                            kind = "EPISODE", type = MediaType.TV, titleId = f.showId, title = d.title,
                            subtitle = last.name.ifBlank { "New episode" }, season = last.season, episode = last.number,
                            imagePath = d.backdropPath ?: d.posterPath, createdAt = now,
                        )
                    )
                    Notifier.notify(applicationContext, f.showId, "New episode • ${d.title}", "S${last.season} E${last.number} · ${last.name}")
                }
            }
        }
        if (s.watchlistAlerts) {
            val today = LocalDate.now().toString()
            for (w in lib.watchlistAll()) {
                if (w.notified) continue
                val t = db.titleDao().get(w.type, w.id) ?: continue
                val added = Instant.ofEpochMilli(w.addedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                if (t.date.isNotBlank() && t.date <= today && t.date > added) {   // was upcoming when saved, now released
                    lib.markWatchNotified(w.type, w.id)
                    nd.insert(
                        NotificationEntity(
                            kind = "PREMIERE", type = w.type, titleId = w.id, title = t.title, subtitle = "Now available",
                            season = 0, episode = 0, imagePath = t.backdropPath ?: t.posterPath, createdAt = now,
                        )
                    )
                    Notifier.notify(applicationContext, w.id, t.title, "From your list. It's out now.")
                }
            }
        }
        return Result.success()
    }
}

object SyncScheduler {
    private const val NAME = "episode_sync"
    fun update(ctx: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) { wm.cancelUniqueWork(NAME); return }
        val req = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
