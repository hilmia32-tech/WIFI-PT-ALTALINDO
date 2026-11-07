package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.notification.NotificationHelper
import com.example.viewmodel.PortalViewModel
import com.example.webview.WebAppNotificationBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context matches Altalindo`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Altalindo", appName)
  }

  @Test
  fun `portal viewmodel handles verification and badge updates`() {
    val viewModel = PortalViewModel()
    assertEquals(0, viewModel.uiState.value.badgeCount)

    viewModel.onBadgeUpdated(3)
    assertEquals(3, viewModel.uiState.value.badgeCount)

    viewModel.onVerificationDetected(5, "Verifikasi Masuk", "Ada 5 permohonan baru")
    assertEquals(5, viewModel.uiState.value.badgeCount)
    assertEquals(1, viewModel.uiState.value.verificationHistory.size)
    assertEquals("Verifikasi Masuk", viewModel.uiState.value.verificationHistory[0].title)

    viewModel.clearBadge()
    assertEquals(0, viewModel.uiState.value.badgeCount)
  }

  @Test
  fun `bridge reports 10-second check interval`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = WebAppNotificationBridge(
      context = context,
      onVerificationDetected = { _, _, _ -> },
      onBadgeUpdated = { _ -> }
    )
    assertEquals(10, bridge.getCheckIntervalSeconds())
  }
}
