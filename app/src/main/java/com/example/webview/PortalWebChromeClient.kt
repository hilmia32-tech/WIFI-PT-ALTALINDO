package com.example.webview

import android.os.Message
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class PortalWebChromeClient(
  private val onProgressChangedCallback: (progress: Int) -> Unit,
  private val onTitleReceivedCallback: (title: String?) -> Unit,
  private val onExternalUrlHandler: ((String) -> Boolean)? = null
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
    return super.onJsAlert(view, url, message, result)
  }

  override fun onCreateWindow(
    view: WebView?,
    isDialog: Boolean,
    isUserGesture: Boolean,
    resultMsg: Message?
  ): Boolean {
    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
    val tempWebView = WebView(view?.context ?: return false)
    tempWebView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
        val targetUrl = request?.url?.toString() ?: return false
        onExternalUrlHandler?.invoke(targetUrl)
        return true
      }

      @Deprecated("Deprecated in Java")
      override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean {
        if (!url.isNullOrBlank()) {
          onExternalUrlHandler?.invoke(url)
        }
        return true
      }
    }
    transport.webView = tempWebView
    resultMsg.sendToTarget()
    return true
  }
}
