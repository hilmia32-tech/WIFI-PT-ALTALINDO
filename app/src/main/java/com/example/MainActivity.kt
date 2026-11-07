package com.example

import android.content.Intent
import android.os.Bundle
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize the notification channel
    NotificationHelper.createNotificationChannel(this)

    // Handle intent extras if opened from a notification
    handleNotificationIntent(intent)

    setContent {
      MyApplicationTheme {
        PortalScreen(
          viewModel = portalViewModel,
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
      if (count > 0) {
        portalViewModel.onBadgeUpdated(count)
      }
    }
  }
}
