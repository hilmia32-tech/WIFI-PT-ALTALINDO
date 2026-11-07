package com.example.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import com.example.notification.NotificationHelper

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
      val msg = message?.takeIf { it.isNotBlank() } ?: "Ada $safeCount pembayaran menunggu verifikasi!"
      onBadgeUpdated(safeCount)
      onVerificationDetected(safeCount, title, msg)
      NotificationHelper.showLocalNotification(context, msg, safeCount)
    }
  }

  @JavascriptInterface
  fun onVerificationCheckResult(count: Int, details: String?) {
    Log.d(TAG, "onVerificationCheckResult: count=$count, details=$details")
    mainHandler.post {
      if (count > 0) {
        val title = "Verifikasi Pembayaran ($count)"
        val msg = details?.takeIf { it.isNotBlank() }
          ?: "Ada $count pembayaran menunggu verifikasi!"
        onBadgeUpdated(count)
        onVerificationDetected(count, title, msg)
        NotificationHelper.showLocalNotification(context, msg, count)
      } else {
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
      NotificationHelper.showLocalNotification(context, message, 1)
    }
  }

  @JavascriptInterface
  fun updateBadge(count: Int) {
    Log.d(TAG, "updateBadge: count=$count")
    mainHandler.post {
      onBadgeUpdated(count.coerceAtLeast(0))
      if (count > 0) {
        val msg = "Ada $count pembayaran menunggu verifikasi!"
        NotificationHelper.showLocalNotification(context, msg, count)
        onVerificationDetected(count, "Verifikasi Pembayaran", msg)
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
      updateBadge(1)
    }
  }
}
