package com.example.webview

import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView

class PortalWebChromeClient(
  private val onProgressChangedCallback: (progress: Int) -> Unit,
  private val onTitleReceivedCallback: (title: String?) -> Unit
) : WebChromeClient() {

  override fun onProgressChanged(view: WebView?, newProgress: Int) {
    super.onProgressChanged(view, newProgress)
    onProgressChangedCallback(newProgress)
  }

  override fun onReceivedTitle(view: WebView?, title: String?) {
    super.onReceivedTitle(view, title)
    onTitleReceivedCallback(title)
  }

  override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
    // Allow default alert handling or let system show it
    return super.onJsAlert(view, url, message, result)
  }
}
