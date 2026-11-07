package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.notification.NotificationHelper
import com.example.ui.PortalScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PortalViewModel

class MainActivity : ComponentActivity() {

  private val portalViewModel: PortalViewModel by viewModels()

  /**
   * Memicu notifikasi lokal secara dinamis.
   * Jika count == 0, notifikasi di bilah atas layar langsung dihapus bersih.
   */
  fun showLocalNotification(message: String, count: Int = 1) {
    if (count <= 0) {
      NotificationHelper.clearNotification(this)
      portalViewModel.onBadgeUpdated(0)
    } else {
      NotificationHelper.showLocalNotification(this, message, count)
      portalViewModel.onBadgeUpdated(count)
      portalViewModel.onVerificationDetected(count, "Verifikasi Pembayaran", message)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Inisialisasi Notification Channel sistem Android
    NotificationHelper.createNotificationChannel(this)
    NotificationHelper.createServiceNotificationChannel(this)

    // Mulai Foreground Service pemantauan latar belakang saat app ditutup total
    com.example.service.AltalindoBackgroundService.startService(this)

    // Handle intent extras jika dibuka dari ketukan notifikasi
    handleNotificationIntent(intent)

    setContent {
      MyApplicationTheme {
        PortalScreen(
          viewModel = portalViewModel,
          onShowLocalNotification = { msg, count ->
            showLocalNotification(msg, count)
          },
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNotificationIntent(intent)
  }

  private fun handleNotificationIntent(intent: Intent?) {
    if (intent?.getStringExtra("EXTRA_SOURCE") == "NOTIFICATION_VERIF") {
      val count = intent.getIntExtra("EXTRA_COUNT", 0)
      portalViewModel.onBadgeUpdated(count)
      if (count <= 0) {
        NotificationHelper.clearNotification(this)
      }
    }
  }
}
