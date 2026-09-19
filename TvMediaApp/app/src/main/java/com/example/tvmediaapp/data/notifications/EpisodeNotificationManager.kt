package com.example.tvmediaapp.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.tvmediaapp.MainActivity
import com.example.tvmediaapp.R
import com.example.tvmediaapp.data.models.Movie

object EpisodeNotificationManager {
    private const val CHANNEL_ID = "showhub_new_episodes"
    private const val CHANNEL_NAME = "Новые серии ShowHub TV"
    private const val CHANNEL_DESC = "Уведомления о выходе новых серий отслеживаемых сериалов"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyNewEpisodes(context: Context, movie: Movie, newCount: Int) {
        try {
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_movie_id", movie.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                movie.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = "Вышли новые серии!"
            val message = if (newCount > 1) {
                "Сериал «${movie.title}»: доступно +$newCount новых серий"
            } else {
                "Сериал «${movie.title}»: вышла новая серия"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_movie)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(movie.id.hashCode(), notification)
        } catch (_: Exception) {}
    }

    fun notifyTodayEpisode(context: Context, movie: Movie, episodeDetails: String) {
        try {
            val prefs = context.getSharedPreferences("showhub_schedule_notifs", Context.MODE_PRIVATE)
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val key = "today_${movie.id}_$todayStr"
            if (prefs.getBoolean(key, false)) return
            prefs.edit().putBoolean(key, true).apply()

            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_movie_id", movie.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                (movie.id + "_today").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = "Сегодня новая серия!"
            val message = "Сериал «${movie.title}»: сегодня выходит $episodeDetails"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_movie)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify((movie.id + "_today").hashCode(), notification)
        } catch (_: Exception) {}
    }
}
