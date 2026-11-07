package com.example.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import com.example.notification.NotificationHelper

/**
 * Interface Javascript untuk menerima perintah dari WebView di portal Altalindo.
 * Terdaftar sebagai "AndroidBridge" dan "AndroidNotificationBridge".
 */
class WebAppInterface(
  private val context: Context,
  private val onBadgeChanged: (Int) -> Unit = {},
  private val onVerificationReceived: (count: Int, title: String?, message: String?) -> Unit = { _, _, _ -> }
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  companion object {
    const val BRIDGE_NAME = "AndroidBridge"
    const val ALT_NAME = "AndroidNotificationBridge"
    const val TAG = "WebAppInterface"
  }

  @JavascriptInterface
  fun updateBadge(count: Int) {
    Log.d(TAG, "updateBadge dipanggil dengan count=$count")
    mainHandler.post {
      onBadgeChanged(count.coerceAtLeast(0))
      // Di sinilah logika notifikasi sistem Android, suara, dan badge merah dijalankan
      if (count > 0) {
        // Memicu notifikasi lokal & suara di HP Android
        showLocalNotification("Ada $count pembayaran menunggu verifikasi!")
        onVerificationReceived(count, "Verifikasi Pembayaran", "Ada $count pembayaran menunggu verifikasi!")
      }
    }
  }

  @JavascriptInterface
  fun updateBadge(countStr: String) {
    val count = countStr.trim().toIntOrNull() ?: 1
    updateBadge(count)
  }

  @JavascriptInterface
  fun showLocalNotification(message: String) {
    Log.d(TAG, "showLocalNotification: $message")
    mainHandler.post {
      NotificationHelper.showLocalNotification(context, message, count = 1)
      onVerificationReceived(1, "Verifikasi Pembayaran", message)
    }
  }

  @JavascriptInterface
  fun onNewVerification(count: Int, title: String?, message: String?) {
    mainHandler.post {
      val safeCount = if (count <= 0) 1 else count
      val msg = message?.takeIf { it.isNotBlank() } ?: "Ada $safeCount pembayaran menunggu verifikasi!"
      onBadgeChanged(safeCount)
      onVerificationReceived(safeCount, title ?: "Verifikasi Pembayaran", msg)
      NotificationHelper.showLocalNotification(context, msg, safeCount)
    }
  }

  @JavascriptInterface
  fun onVerificationCheckResult(count: Int, details: String?) {
    mainHandler.post {
      if (count > 0) {
        val msg = details?.takeIf { it.isNotBlank() } ?: "Ada $count pembayaran menunggu verifikasi!"
        onBadgeChanged(count)
        onVerificationReceived(count, "Verifikasi Pembayaran", msg)
        NotificationHelper.showLocalNotification(context, msg, count)
      }
    }
  }

  @JavascriptInterface
  fun triggerTestVerification() {
    mainHandler.post {
      updateBadge(1)
    }
  }
}
