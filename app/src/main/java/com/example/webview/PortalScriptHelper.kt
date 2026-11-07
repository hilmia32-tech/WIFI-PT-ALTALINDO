package com.example.webview

object PortalScriptHelper {

  /**
   * Script otomatis yang diinjeksikan ke dalam WebView portal Altalindo.
   * Melakukan scan setiap 10 detik untuk mendeteksi status pembayaran / verifikasi masuk,
   * lalu memanggil interface AndroidBridge.updateBadge(count).
   */
  fun getAutoCheckScript(): String {
    return """
      (function() {
        if (window.__altalindoScriptInitialized) {
          return "ALREADY_INITIALIZED";
        }
        window.__altalindoScriptInitialized = true;
        
        console.log("[Altalindo] Skrip pemantau verifikasi pembayaran 10 detik aktif.");

        // Hook pembantu global agar dapat dipanggil langsung dari skrip blogger / admin
        window.AltalindoApp = {
          triggerVerification: function(count, message) {
            count = parseInt(count, 10) || 1;
            if (window.AndroidBridge && typeof window.AndroidBridge.updateBadge === 'function') {
              window.AndroidBridge.updateBadge(count);
            } else if (window.AndroidNotificationBridge) {
              window.AndroidNotificationBridge.updateBadge(count);
            }
          },
          notifyPayment: function(count) {
            this.triggerVerification(count || 1);
          },
          checkNow: function() {
            window.__altalindoCheckVerification();
          }
        };

        // Algoritma pendeteksi verifikasi pembayaran masuk
        window.__altalindoCheckVerification = function() {
          try {
            var count = 0;
            var details = [];

            // 1. Periksa elemen badge / counter angka
            var badgeSelectors = [
              '.badge', '.notif-badge', '.notification-badge', '.notification-count',
              '.verif-count', '.verifikasi-count', '[data-badge]', '[data-notification]',
              '.pending-verification', '.badge-danger', '.badge-warning', '.alert-count',
              '.payment-pending', '.count-pembayaran'
            ];

            var badgeEls = document.querySelectorAll(badgeSelectors.join(','));
            for (var i = 0; i < badgeEls.length; i++) {
              var el = badgeEls[i];
              var txt = (el.innerText || el.textContent || '').trim();
              var num = parseInt(txt, 10);
              if (!isNaN(num) && num > 0) {
                count += num;
                details.push("Badge: " + num);
              }
            }

            // 2. Periksa teks verifikasi pembayaran dalam elemen halaman
            var paymentRegex = /(pembayaran|verifikasi|menunggu\s+verifikasi|bukti\s+transfer|konfirmasi\s+pembayaran|transaksi\s+baru|pesanan\s+baru|unverified)/i;
            var textElements = document.querySelectorAll('div, span, p, a, h3, h4, li, strong, b, td');
            var foundItems = [];

            for (var j = 0; j < textElements.length && foundItems.length < 5; j++) {
              var node = textElements[j];
              if (node.children.length === 0) {
                var content = (node.innerText || node.textContent || '').trim();
                if (content && paymentRegex.test(content) && content.length > 3 && content.length < 100) {
                  // Periksa apakah ini status aktif seperti 'menunggu verifikasi' atau ada angka
                  if (/menunggu|pending|belum\s+diverifikasi|verifikasi\s+masuk|bukti\s+transfer/i.test(content)) {
                    foundItems.push(content);
                  }
                }
              }
            }

            // Jika ada status menunggu verifikasi ditemukan
            if (count === 0 && foundItems.length > 0) {
              count = foundItems.length;
            }

            // 3. Periksa variabel JavaScript global jika webmaster mengesetnya
            if (typeof window.pendingPayments === 'number' && window.pendingPayments > 0) {
              count = Math.max(count, window.pendingPayments);
            }
            if (typeof window.pendingVerifications === 'number' && window.pendingVerifications > 0) {
              count = Math.max(count, window.pendingVerifications);
            }

            // Laporkan ke AndroidBridge
            if (count > 0) {
              if (window.AndroidBridge && typeof window.AndroidBridge.updateBadge === 'function') {
                window.AndroidBridge.updateBadge(count);
              } else if (window.AndroidNotificationBridge) {
                window.AndroidNotificationBridge.updateBadge(count);
              }
            }

            return JSON.stringify({ count: count, items: foundItems });
          } catch(err) {
            console.error("[Altalindo] Cek verifikasi error:", err);
            return JSON.stringify({ error: err.message });
          }
        };

        // Jalankan pengecekan pertama setelah jeda singkat
        setTimeout(function() {
          window.__altalindoCheckVerification();
        }, 1500);

        // Pasang interval otomatis setiap 10 DETIK (10000 ms)
        if (window.__altalindoIntervalId) {
          clearInterval(window.__altalindoIntervalId);
        }
        window.__altalindoIntervalId = setInterval(function() {
          window.__altalindoCheckVerification();
        }, 10000);

        return "INITIALIZED_10_SECONDS";
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
