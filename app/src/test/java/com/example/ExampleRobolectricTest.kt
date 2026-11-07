package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.notification.NotificationHelper
import com.example.service.AltalindoBackgroundService
import com.example.viewmodel.PortalViewModel
import com.example.webview.PortalWebViewClient
import com.example.webview.WebAppInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

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
  fun `webAppInterface dynamic sync resets to 0 when pendingCount is 0`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var currentBadge = -1
    val bridge = WebAppInterface(
      context = context,
      onBadgeChanged = { count ->
        currentBadge = count
      }
    )

    // Simulasi ada 2 pembayaran
    bridge.updateBadge(2)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assertEquals(2, currentBadge)

    // Simulasi saat tabel konfirmasi sudah kosong (pendingCount bernilai 0)
    bridge.updateBadge(0)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assertEquals(0, currentBadge)
  }

  @Test
  fun `portal webview client intercepts whatsapp urls successfully`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val client = PortalWebViewClient(
      context = context,
      onLoadingStateChanged = {},
      onErrorReceived = {},
      onPageFinishedHook = {}
    )

    // Tautan wa.me harus dicegat dan dibuka secara eksternal (mengembalikan true)
    assertTrue(client.handleUrl(null, "https://wa.me/6281234567890?text=Halo%20Altalindo"))
    assertTrue(client.handleUrl(null, "https://api.whatsapp.com/send?phone=6281234567890"))
    assertTrue(client.handleUrl(null, "whatsapp://send?phone=6281234567890"))

    // Tautan portal internal altalindo tetap dimuat di dalam WebView (mengembalikan false)
    assertFalse(client.handleUrl(null, "https://altalindo.blogspot.com/p/kontak.html"))
  }

  @Test
  fun `foreground service notification builds successfully`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    NotificationHelper.createServiceNotificationChannel(context)
    val notif = NotificationHelper.buildForegroundServiceNotification(context)
    assertNotNull(notif)
  }
}
