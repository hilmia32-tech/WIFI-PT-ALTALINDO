package com.example.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import com.example.notification.NotificationHelper

/**
 * JavaScript Interface bridge injected into WebView as "AndroidNotificationBridge"
 * and "AndroidBridge". Enables two-way communication between the Altalindo web portal
 * and native Android code.
 */
class WebAppNotificationBridge(
  private val context: Context,
  private val onVerificationDetected: (count: Int, title: String?, message: String?) -> Unit,
  private val onBadgeUpdated: (count: Int) -> Unit,
  private val onLogReceived: (String) -> Unit = {}
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  companion object {
    const val BRIDGE_NAME = "AndroidNotificationBridge"
    const val ALIAS_NAME = "AndroidBridge"
    const val TAG = "AltalindoBridge"
    const val CHECK_INTERVAL_SECONDS = 10
  }

  @JavascriptInterface
  fun onNewVerification(count: Int, title: String?, message: String?) {
    Log.d(TAG, "onNewVerification received: count=$count, title=$title, msg=$message")
    mainHandler.post {
      val safeCount = if (count <= 0) 1 else count
      onBadgeUpdated(safeCount)
      onVerificationDetected(safeCount, title, message)
      NotificationHelper.showVerificationNotification(context, safeCount, title, message)
    }
  }

  @JavascriptInterface
  fun onVerificationCheckResult(count: Int, details: String?) {
    Log.d(TAG, "onVerificationCheckResult: count=$count, details=$details")
    mainHandler.post {
      if (count > 0) {
        val title = "Verifikasi Masuk Baru ($count)"
        val msg = details?.takeIf { it.isNotBlank() }
          ?: "Terdeteksi $count item verifikasi di portal Altalindo."
        onBadgeUpdated(count)
        onVerificationDetected(count, title, msg)
        NotificationHelper.showVerificationNotification(context, count, title, msg)
      } else {
        // No pending verification in this cycle
        onLogReceived("Pengecekan selesai: Belum ada verifikasi masuk baru.")
      }
    }
  }

  @JavascriptInterface
  fun showNotification(title: String, message: String) {
    Log.d(TAG, "showNotification: title=$title, msg=$message")
    mainHandler.post {
      onBadgeUpdated(1)
      onVerificationDetected(1, title, message)
      NotificationHelper.showVerificationNotification(context, 1, title, message)
    }
  }

  @JavascriptInterface
  fun updateBadge(count: Int) {
    Log.d(TAG, "updateBadge: count=$count")
    mainHandler.post {
      onBadgeUpdated(count.coerceAtLeast(0))
      if (count > 0) {
        NotificationHelper.showVerificationNotification(
          context,
          count,
          "Pembaruan Verifikasi",
          "Terdapat $count verifikasi masuk pada portal Altalindo."
        )
      }
    }
  }

  @JavascriptInterface
  fun getCheckIntervalSeconds(): Int {
    return CHECK_INTERVAL_SECONDS
  }

  @JavascriptInterface
  fun log(message: String) {
    Log.d(TAG, "WebLog: $message")
    mainHandler.post {
      onLogReceived(message)
    }
  }

  @JavascriptInterface
  fun triggerTestVerification() {
    mainHandler.post {
      val testCount = 1
      val testTitle = "Uji Coba: Verifikasi Masuk Baru!"
      val testMessage = "Sistem deteksi 10 detik berfungsi dengan baik pada portal Altalindo."
      onBadgeUpdated(testCount)
      onVerificationDetected(testCount, testTitle, testMessage)
      NotificationHelper.showVerificationNotification(context, testCount, testTitle, testMessage)
    }
  }
}
