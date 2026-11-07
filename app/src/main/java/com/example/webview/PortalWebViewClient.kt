package com.example.webview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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

  override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
    onLoadingStateChanged(true)
    onErrorReceived(null) // clear previous errors
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    onLoadingStateChanged(false)

    // Inject automated 10-second verification checker script
    view?.evaluateJavascript(PortalScriptHelper.getAutoCheckScript()) { result ->
      // Script injected successfully
    }

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
    val scheme = request.url.scheme?.lowercase()

    // Handle special intents like tel:, mailto:, whatsapp:, etc.
    if (scheme != "http" && scheme != "https") {
      try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
        return true
      } catch (_: Exception) {
        return true
      }
    }

    // Load web URLs inside this WebView directly
    return false
  }
}
