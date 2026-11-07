package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

  const val CHANNEL_ID = "altalindo_pembayaran_channel_v1"
  private const val CHANNEL_NAME = "Verifikasi Pembayaran Altalindo"
  private const val CHANNEL_DESCRIPTION = "Notifikasi suara dan getar untuk verifikasi pembayaran masuk"
  private const val NOTIFICATION_ID = 2024

  fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val importance = NotificationManager.IMPORTANCE_HIGH
      val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
        description = CHANNEL_DESCRIPTION
        enableLights(true)
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 400, 200, 400)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttributes = AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
          .build()
        setSound(soundUri, audioAttributes)
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  /**
   * Triggers local system notification, audible phone ringtone sound, and physical vibration.
   */
  fun showLocalNotification(
    context: Context,
    message: String,
    count: Int = 1
  ) {
    Log.d("NotificationHelper", "showLocalNotification: $message (count=$count)")

    createNotificationChannel(context)

    // 1. Play audible ringtone sound directly so the phone is guaranteed to ring
    try {
      val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
      val ringtone = RingtoneManager.getRingtone(context.applicationContext, soundUri)
      ringtone?.play()
    } catch (e: Exception) {
      Log.e("NotificationHelper", "Error playing sound: ${e.message}")
    }

    // 2. Trigger phone vibration
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator?.vibrate(
          VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350), -1)
        )
      } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350), -1))
        } else {
          @Suppress("DEPRECATION")
          vibrator?.vibrate(longArrayOf(0, 350, 150, 350), -1)
        }
      }
    } catch (e: Exception) {
      Log.e("NotificationHelper", "Error vibrating: ${e.message}")
    }

    // 3. Build and display system heads-up notification
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

    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    val title = if (count > 1) {
      "Verifikasi Pembayaran Masuk ($count)"
    } else {
      "Verifikasi Pembayaran Masuk!"
    }

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setSound(soundUri)
      .setVibrate(longArrayOf(0, 400, 200, 400))
      .setAutoCancel(true)
      .setNumber(count)
      .setContentIntent(pendingIntent)

    try {
      val notificationManager = NotificationManagerCompat.from(context)
      notificationManager.notify(NOTIFICATION_ID, builder.build())
    } catch (e: SecurityException) {
      Log.e("NotificationHelper", "Permission missing for notification: ${e.message}")
    }
  }
}
