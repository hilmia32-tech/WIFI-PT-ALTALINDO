package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

  const val CHANNEL_ID = "altalindo_verification_channel"
  private const val CHANNEL_NAME = "Verifikasi Altalindo"
  private const val CHANNEL_DESCRIPTION = "Pemberitahuan verifikasi masuk otomatis setiap 10 detik"
  private const val NOTIFICATION_ID = 2001

  fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val importance = NotificationManager.IMPORTANCE_HIGH
      val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
        description = CHANNEL_DESCRIPTION
        enableLights(true)
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 250, 150, 250)
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .setUsage(AudioAttributes.USAGE_NOTIFICATION)
          .build()
        setSound(defaultSound, audioAttributes)
      }

      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  fun showVerificationNotification(
    context: Context,
    count: Int,
    customTitle: String? = null,
    customMessage: String? = null
  ) {
    createNotificationChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra("EXTRA_SOURCE", "NOTIFICATION_VERIF")
      putExtra("EXTRA_COUNT", count)
    }

    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val title = customTitle?.takeIf { it.isNotBlank() }
      ?: if (count > 1) {
        "Verifikasi Masuk Baru ($count)"
      } else {
        "Verifikasi Masuk Baru!"
      }

    val message = customMessage?.takeIf { it.isNotBlank() }
      ?: "Ada $count aktivitas / verifikasi baru di portal Altalindo yang perlu dicek."

    val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setSound(defaultSoundUri)
      .setVibrate(longArrayOf(0, 250, 150, 250))
      .setAutoCancel(true)
      .setNumber(count)
      .setContentIntent(pendingIntent)

    try {
      val notificationManager = NotificationManagerCompat.from(context)
      if (notificationManager.areNotificationsEnabled()) {
        notificationManager.notify(NOTIFICATION_ID, builder.build())
      }
    } catch (_: SecurityException) {
      // Handled gracefully when permission is not granted yet
    }
  }
}
