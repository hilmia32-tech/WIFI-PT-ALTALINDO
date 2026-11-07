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
    Log.d(TAG, "updateBadge dipanggil secara dinamis dengan count=$count")
    mainHandler.post {
      val safeCount = count.coerceAtLeast(0)
      // Selalu perbarui badge dan state aplikasi secara dinamis
      onBadgeChanged(safeCount)

      if (safeCount == 0) {
        // Nilai 0: Hapus notifikasi dari bilah atas layar secara bersih
        NotificationHelper.clearNotification(context)
        onVerificationReceived(0, "Verifikasi Pembayaran", "Tidak ada pembayaran menunggu verifikasi.")
        Log.d(TAG, "Notifikasi dibersihkan karena pendingCount bernilai 0.")
      } else {
        // Nilai > 0: Tampilkan notifikasi dan bunyikan suara HP
        val msg = "Ada $safeCount pembayaran menunggu verifikasi!"
        NotificationHelper.showLocalNotification(context, msg, safeCount)
        onVerificationReceived(safeCount, "Verifikasi Pembayaran", msg)
        Log.d(TAG, "Notifikasi dimunculkan: $msg")
      }
    }
  }

  @JavascriptInterface
  fun updateBadge(countStr: String) {
    val count = countStr.trim().toIntOrNull() ?: 0
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
      val safeCount = count.coerceAtLeast(0)
      onBadgeChanged(safeCount)
      if (safeCount == 0) {
        NotificationHelper.clearNotification(context)
        onVerificationReceived(0, "Verifikasi Pembayaran", "Tidak ada pembayaran menunggu verifikasi.")
      } else {
        val msg = message?.takeIf { it.isNotBlank() } ?: "Ada $safeCount pembayaran menunggu verifikasi!"
        onVerificationReceived(safeCount, title ?: "Verifikasi Pembayaran", msg)
        NotificationHelper.showLocalNotification(context, msg, safeCount)
      }
    }
  }

  @JavascriptInterface
  fun onVerificationCheckResult(count: Int, details: String?) {
    mainHandler.post {
      val safeCount = count.coerceAtLeast(0)
      onBadgeChanged(safeCount)
      if (safeCount == 0) {
        NotificationHelper.clearNotification(context)
      } else {
        val msg = details?.takeIf { it.isNotBlank() } ?: "Ada $safeCount pembayaran menunggu verifikasi!"
        onVerificationReceived(safeCount, "Verifikasi Pembayaran", msg)
        NotificationHelper.showLocalNotification(context, msg, safeCount)
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
