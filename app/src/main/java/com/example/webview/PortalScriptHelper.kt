package com.example.webview

object PortalScriptHelper {

  /**
   * Script otomatis yang diinjeksikan ke dalam WebView portal Altalindo.
   * Melakukan scan setiap 10 detik secara dinamis.
   * Jika tabel konfirmasi kosong atau count = 0, secara eksplisit mengirimkan
   * updateBadge(0) agar notifikasi di bilah atas layar HP terhapus seketika.
   */
  fun getAutoCheckScript(): String {
    return """
      (function() {
        if (window.__altalindoScriptInitialized) {
          return "ALREADY_INITIALIZED";
        }
        window.__altalindoScriptInitialized = true;
        
        console.log("[Altalindo] Skrip sinkronisasi dinamis verifikasi pembayaran aktif.");

        // Hook global untuk pengujian atau integrasi langsung dari portal
        window.AltalindoApp = {
          triggerVerification: function(count) {
            var val = (count !== undefined && count !== null) ? parseInt(count, 10) : 0;
            if (isNaN(val) || val < 0) val = 0;
            if (window.AndroidBridge && typeof window.AndroidBridge.updateBadge === 'function') {
              window.AndroidBridge.updateBadge(val);
            }
          },
          setCount: function(count) {
            this.triggerVerification(count);
          },
          checkNow: function() {
            window.__altalindoCheckVerification();
          }
        };

        // Algoritma pendeteksi verifikasi pembayaran masuk yang dinamis
        window.__altalindoCheckVerification = function() {
          try {
            var count = 0;

            // 1. Prioritas Utama: Periksa variabel dinamis dari sistem portal jika tersedia
            if (typeof window.pendingCount === 'number') {
              count = Math.max(0, window.pendingCount);
            } else if (typeof window.pendingPayments === 'number') {
              count = Math.max(0, window.pendingPayments);
            } else if (typeof window.pendingVerifications === 'number') {
              count = Math.max(0, window.pendingVerifications);
            } else {
              // 2. Periksa tabel konfirmasi / verifikasi pembayaran di halaman
              var tables = document.querySelectorAll('table, .table, #table-konfirmasi, #tabel-pembayaran, .tabel-verifikasi');
              var foundTableWithData = false;

              for (var t = 0; t < tables.length; t++) {
                var table = tables[t];
                var rows = table.querySelectorAll('tbody tr');
                if (rows.length === 0) {
                  // Fallback ke tr biasa jika tanpa tbody (kecuali header th)
                  var allTr = table.querySelectorAll('tr');
                  var validRows = [];
                  for (var r = 0; r < allTr.length; r++) {
                    if (allTr[r].querySelectorAll('th').length === 0) {
                      validRows.push(allTr[r]);
                    }
                  }
                  rows = validRows;
                }

                if (rows.length > 0) {
                  var activePendingRows = 0;
                  for (var i = 0; i < rows.length; i++) {
                    var rowText = (rows[i].innerText || rows[i].textContent || '').toLowerCase().trim();
                    
                    // Lewati jika baris menampilkan pesan 'kosong' / 'tidak ada data'
                    var isEmptyRow = /(tidak\s+ada|belum\s+ada|kosong|no\s+data|data\s+tidak\s+ditemukan|none)/.test(rowText);
                    if (isEmptyRow) {
                      continue;
                    }

                    // Periksa apakah baris memiliki tanda status menunggu verifikasi / pending
                    if (/(menunggu|pending|belum\s+diverifikasi|verifikasi|konfirmasi|bukti)/.test(rowText)) {
                      activePendingRows++;
                    }
                  }

                  foundTableWithData = true;
                  count = activePendingRows;
                  break;
                }
              }

              // 3. Jika tidak ada baris tabel aktif, cek elemen badge angka spesifik
              if (!foundTableWithData) {
                var badgeEls = document.querySelectorAll(
                  '.badge-pending, .notif-count, .payment-pending, [data-pending-count], #count-verifikasi'
                );
                for (var b = 0; b < badgeEls.length; b++) {
                  var bTxt = (badgeEls[b].innerText || badgeEls[b].textContent || '').trim();
                  var bNum = parseInt(bTxt, 10);
                  if (!isNaN(bNum)) {
                    count = Math.max(0, bNum);
                    break;
                  }
                }
              }
            }

            // SELALU kirimkan nilai count aktual (termasuk 0) ke AndroidBridge
            // Ini memastikan notifikasi langsung terhapus saat count = 0
            if (window.AndroidBridge && typeof window.AndroidBridge.updateBadge === 'function') {
              window.AndroidBridge.updateBadge(count);
            } else if (window.AndroidNotificationBridge && typeof window.AndroidNotificationBridge.updateBadge === 'function') {
              window.AndroidNotificationBridge.updateBadge(count);
            }

            return JSON.stringify({ count: count, timestamp: new Date().toLocaleTimeString() });
          } catch(err) {
            console.error("[Altalindo] Sinkronisasi error:", err);
            return JSON.stringify({ error: err.message });
          }
        };

        // Jalankan pemeriksaan awal
        setTimeout(function() {
          window.__altalindoCheckVerification();
        }, 1200);

        // Jalankan berulang secara otomatis setiap 10 detik
        if (window.__altalindoIntervalId) {
          clearInterval(window.__altalindoIntervalId);
        }
        window.__altalindoIntervalId = setInterval(function() {
          window.__altalindoCheckVerification();
        }, 10000);

        return "DYNAMIC_SYNC_INITIALIZED";
      })();
    """.trimIndent()
  }

  fun getPeriodicCheckScript(): String {
    return """
      (function() {
        if (typeof window.__altalindoCheckVerification === 'function') {
          return window.__altalindoCheckVerification();
        } else {
          ${getAutoCheckScript()}
        }
      })();
    """.trimIndent()
  }
}
