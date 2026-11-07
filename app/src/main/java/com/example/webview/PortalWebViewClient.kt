package com.example.webview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class PortalWebViewClient(
  private val context: Context,
  private val onLoadingStateChanged: (isLoading: Boolean) -> Unit,
  private val onErrorReceived: (errorMessage: String?) -> Unit,
  private val onPageFinishedHook: (url: String?) -> Unit
) : WebViewClient() {

  companion object {
    private const val TAG = "PortalWebViewClient"
  }

  override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
    onLoadingStateChanged(true)
    onErrorReceived(null)
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    onLoadingStateChanged(false)

    // Inject automated 10-second verification checker script
    view?.evaluateJavascript(PortalScriptHelper.getAutoCheckScript()) { _ -> }
    onPageFinishedHook(url)
  }

  override fun onReceivedError(
    view: WebView?,
    request: WebResourceRequest?,
    error: WebResourceError?
  ) {
    super.onReceivedError(view, request, error)
    if (request?.isForMainFrame == true) {
      val desc = error?.description?.toString() ?: "Gagal memuat portal. Periksa koneksi internet."
      onErrorReceived(desc)
      onLoadingStateChanged(false)
    }
  }

  override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
    val url = request?.url?.toString() ?: return false
    return handleUrl(view, url)
  }

  @Deprecated("Deprecated in Java")
  override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return handleUrl(view, url)
  }

  /**
   * Mendeteksi tautan WhatsApp (wa.me, api.whatsapp.com, whatsapp://) dan URL eksternal
   * agar dialihkan langsung ke aplikasi WhatsApp atau browser eksternal.
   */
  fun handleUrl(view: WebView?, url: String): Boolean {
    Log.d(TAG, "Mengecek URL: $url")
    val uri = try {
      Uri.parse(url)
    } catch (_: Exception) {
      return false
    }

    val scheme = uri.scheme?.lowercase().orEmpty()
    val host = uri.host?.lowercase().orEmpty()

    // 1. Deteksi tautan WhatsApp: wa.me, api.whatsapp.com, whatsapp.com/send, atau skema whatsapp://
    val isWhatsApp = scheme == "whatsapp" ||
        host.contains("wa.me") ||
        host.contains("api.whatsapp.com") ||
        url.contains("whatsapp.com/send") ||
        url.contains("whatsapp://")

    if (isWhatsApp) {
      Log.d(TAG, "Tautan WhatsApp terdeteksi: $url")
      val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }

      try {
        // Coba buka langsung ke WhatsApp (atau WhatsApp Business)
        context.startActivity(waIntent)
        return true
      } catch (e: Exception) {
        Log.w(TAG, "Gagal membuka WhatsApp via intent langsung, fallback ke browser: ${e.message}")
        try {
          val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(webIntent)
          return true
        } catch (ex: Exception) {
          Log.e(TAG, "Gagal membuka tautan WhatsApp: ${ex.message}")
          return true
        }
      }
    }

    // 2. Skema non-HTTP seperti tel:, mailto:, sms:, market:, intent:
    if (scheme != "http" && scheme != "https") {
      try {
        if (scheme == "intent") {
          val parsedIntent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(parsedIntent)
          return true
        } else {
          val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(intent)
          return true
        }
      } catch (e: Exception) {
        Log.w(TAG, "Gagal menangani skema non-http: ${e.message}")
        return true
      }
    }

    // 3. Tautan eksternal medsos (Telegram, Instagram) buka di aplikasi eksternal
    if (host.contains("t.me") || host.contains("telegram.me") || host.contains("instagram.com")) {
      try {
        val extIntent = Intent(Intent.ACTION_VIEW, uri).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(extIntent)
        return true
      } catch (_: Exception) {
        return false
      }
    }

    // URL situs Altalindo tetap dimuat di dalam WebView
    return false
  }
}
