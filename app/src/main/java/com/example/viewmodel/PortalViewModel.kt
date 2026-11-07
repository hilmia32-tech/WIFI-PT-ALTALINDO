package com.example.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VerificationEvent(
  val id: Long = System.currentTimeMillis(),
  val count: Int,
  val title: String,
  val message: String,
  val timeFormatted: String
)

data class PortalUiState(
  val currentUrl: String = "https://altalindo.blogspot.com",
  val pageTitle: String = "Altalindo",
  val isLoading: Boolean = true,
  val progress: Int = 0,
  val errorMessage: String? = null,
  val badgeCount: Int = 0,
  val isMonitoringActive: Boolean = true,
  val totalChecksPerformed: Int = 0,
  val lastCheckTime: String = "Memulai...",
  val lastVerificationMessage: String? = null,
  val verificationHistory: List<VerificationEvent> = emptyList(),
  val canGoBack: Boolean = false,
  val canGoForward: Boolean = false
)

class PortalViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(PortalUiState())
  val uiState: StateFlow<PortalUiState> = _uiState.asStateFlow()

  private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

  fun onBadgeUpdated(count: Int) {
    _uiState.update { current ->
      current.copy(badgeCount = count)
    }
  }

  fun onVerificationDetected(count: Int, title: String?, message: String?) {
    val currentTime = timeFormat.format(Date())
    val event = VerificationEvent(
      count = count,
      title = title ?: "Verifikasi Masuk Baru",
      message = message ?: "Verifikasi terdeteksi di portal Altalindo",
      timeFormatted = currentTime
    )

    _uiState.update { current ->
      val updatedList = (listOf(event) + current.verificationHistory).take(30)
      current.copy(
        badgeCount = count,
        lastVerificationMessage = event.message,
        verificationHistory = updatedList,
        lastCheckTime = currentTime
      )
    }
  }

  fun recordCheckCycle() {
    val currentTime = timeFormat.format(Date())
    _uiState.update { current ->
      current.copy(
        totalChecksPerformed = current.totalChecksPerformed + 1,
        lastCheckTime = currentTime
      )
    }
  }

  fun clearBadge() {
    _uiState.update { it.copy(badgeCount = 0) }
  }

  fun setLoading(loading: Boolean) {
    _uiState.update { it.copy(isLoading = loading) }
  }

  fun setProgress(progress: Int) {
    _uiState.update { it.copy(progress = progress) }
  }

  fun setError(error: String?) {
    _uiState.update { it.copy(errorMessage = error) }
  }

  fun setTitle(title: String?) {
    if (!title.isNullOrBlank()) {
      _uiState.update { it.copy(pageTitle = title) }
    }
  }

  fun setNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
    _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
  }
}
