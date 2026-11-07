package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Foreground Service yang tetap aktif melakukan polling berkala ke server
 * bahkan ketika aplikasi ditutup total oleh pengguna (swiped away / kill process).
 */
class AltalindoBackgroundService : Service() {

  companion object {
    private const val TAG = "AltalindoBgService"
    const val PORTAL_URL = "https://altalindo.blogspot.com"
    const val POLLING_INTERVAL_MS = 10_000L // 10 detik

    @Volatile
    var isRunning = false
      private set

    fun startService(context: Context) {
      val intent = Intent(context, AltalindoBackgroundService::class.java)
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Gagal memulai Foreground Service: ${e.message}")
      }
    }

    fun stopService(context: Context) {
      val intent = Intent(context, AltalindoBackgroundService::class.java)
      context.stopService(intent)
    }
  }

  private val serviceJob = Job()
  private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(8, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "AltalindoBackgroundService dibuat.")
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "AltalindoBackgroundService onStartCommand dipanggil.")
    isRunning = true

    // 1. Tampilkan notifikasi Foreground Service wajib agar sistem tidak membunuh proses
    val notification = NotificationHelper.buildForegroundServiceNotification(
      this,
      "Layanan pemantau aktif setiap 10 detik (aplikasi ditutup tetap berjalan)"
    )

    try {
      startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
    } catch (e: Exception) {
      Log.e(TAG, "Error startForeground: ${e.message}")
    }

    // 2. Mulai polling berkala ke server setiap 10 detik
    startPollingLoop()

    // START_STICKY: Beritahu sistem Android agar me-restart layanan jika sempat dimatikan karena RAM
    return START_STICKY
  }

  private fun startPollingLoop() {
    serviceScope.launch {
      Log.d(TAG, "Loop pemantauan server di latar belakang dimulai.")
      while (isActive) {
        try {
          checkServerStatus()
        } catch (e: Exception) {
          Log.w(TAG, "Pengecekan server di latar belakang tertunda: ${e.message}")
        }
        delay(POLLING_INTERVAL_MS)
      }
    }
  }

  /**
   * Mengirim request HTTP ke portal altalindo untuk mendeteksi apakah ada pembayaran / verifikasi masuk.
   */
  private fun checkServerStatus() {
    val request = Request.Builder()
      .url(PORTAL_URL)
      .header("User-Agent", "Mozilla/5.0 (Linux; Android Altalindo-Background-Monitor)")
      .build()

    try {
      httpClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val html = response.body?.string().orEmpty()
          parseAndNotify(html)
        }
      }
    } catch (e: Exception) {
      Log.d(TAG, "Koneksi latar belakang ke portal gagal sementara: ${e.message}")
    }
  }

  /**
   * Menganalisa konten respons HTML dari portal altalindo.
   */
  private fun parseAndNotify(html: String) {
    var detectedCount = 0

    // 1. Periksa variabel JavaScript window.pendingCount / window.pendingPayments
    val jsVarPattern = Pattern.compile("pending(?:Count|Payments|Verifications)\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    val jsMatcher = jsVarPattern.matcher(html)
    if (jsMatcher.find()) {
      detectedCount = jsMatcher.group(1)?.toIntOrNull() ?: 0
    } else {
      // 2. Periksa baris tabel atau tag badge dengan regex
      val tablePattern = Pattern.compile("<tr[^>]*>([\\s\\S]*?)<\\/tr>", Pattern.CASE_INSENSITIVE)
      val rowMatcher = tablePattern.matcher(html)
      var activeRows = 0

      while (rowMatcher.find()) {
        val rowHtml = rowMatcher.group(1)?.lowercase().orEmpty()
        val isEmptyRow = rowHtml.contains("tidak ada") || rowHtml.contains("belum ada") || rowHtml.contains("kosong")
        if (!isEmptyRow && (rowHtml.contains("menunggu") || rowHtml.contains("pending") || rowHtml.contains("bukti transfer") || rowHtml.contains("verifikasi"))) {
          activeRows++
        }
      }

      detectedCount = activeRows
    }

    Log.d(TAG, "Hasil pengecekan latar belakang: detectedCount = $detectedCount")

    if (detectedCount > 0) {
      // Pembayaran masuk ditemukan: bunyikan dering, aktifkan getaran, munculkan notifikasi
      val msg = "Ada $detectedCount pembayaran menunggu verifikasi!"
      NotificationHelper.showLocalNotification(this, msg, detectedCount)
    } else {
      // Bersihkan notifikasi jika tidak ada pembayaran yang menunggu
      NotificationHelper.clearNotification(this)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.d(TAG, "AltalindoBackgroundService dihancurkan.")
    isRunning = false
    serviceJob.cancel()
  }
}
