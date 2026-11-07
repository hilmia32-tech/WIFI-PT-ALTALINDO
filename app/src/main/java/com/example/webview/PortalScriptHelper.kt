package com.example.webview

object PortalScriptHelper {

  /**
   * The JavaScript code injected into the page once loaded.
   * Sets up global helper hooks and starts a 10-second interval timer
   * to automatically check for incoming verification items.
   */
  fun getAutoCheckScript(): String {
    return """
      (function() {
        if (window.__altalindoScriptInitialized) {
          return "ALREADY_INITIALIZED";
        }
        window.__altalindoScriptInitialized = true;
        
        console.log("[Altalindo] Script auto-deteksi verifikasi 10 detik diaktifkan.");

        // Define global helper object for web developers / template code
        window.AltalindoApp = {
          triggerVerification: function(count, title, msg) {
            count = count || 1;
            title = title || "Verifikasi Masuk Baru!";
            msg = msg || "Ada " + count + " verifikasi baru masuk di portal Altalindo.";
            if (window.AndroidNotificationBridge) {
              window.AndroidNotificationBridge.onNewVerification(count, title, msg);
            }
          },
          updateBadge: function(count) {
            if (window.AndroidNotificationBridge) {
              window.AndroidNotificationBridge.updateBadge(count);
            }
          },
          checkNow: function() {
            window.__altalindoCheckVerification();
          }
        };

        // Verification inspection algorithm
        window.__altalindoCheckVerification = function() {
          try {
            var count = 0;
            var details = [];
            var foundTexts = [];

            // 1. Check badge or notification counter elements
            var badgeSelectors = [
              '.badge', '.notif-badge', '.notification-badge', '.notification-count',
              '.verif-count', '.verifikasi-count', '[data-badge]', '[data-notification]',
              '.pending-verification', '.badge-danger', '.badge-warning', '.alert-count'
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

            // 2. Search for verification phrases in text nodes
            var verifRegex = /(verifikasi|verification|perlu\s+verifikasi|menunggu\s+verifikasi|verifikasi\s+masuk|permintaan\s+verifikasi|antrean\s+verifikasi)/i;
            var allElements = document.querySelectorAll('div, span, p, a, h3, h4, li, strong, b');
            
            for (var j = 0; j < allElements.length && foundTexts.length < 3; j++) {
              var node = allElements[j];
              // Check if node is leaf or near-leaf to avoid matching huge parent containers
              if (node.children.length <= 1) {
                var text = (node.innerText || node.textContent || '').trim();
                if (text && verifRegex.test(text) && text.length > 3 && text.length < 120) {
                  // Ignore common menu/static footer links if not active/alert
                  foundTexts.push(text);
                }
              }
            }

            // 3. Custom developer variable check
            if (typeof window.pendingVerifications === 'number' && window.pendingVerifications > 0) {
              count = Math.max(count, window.pendingVerifications);
            }
            if (typeof window.altalindoVerificationCount === 'number' && window.altalindoVerificationCount > 0) {
              count = Math.max(count, window.altalindoVerificationCount);
            }

            // Combine details
            var summary = details.join(', ');
            if (foundTexts.length > 0) {
              summary += (summary ? " | " : "") + foundTexts.join('; ');
            }

            // Report to Android Bridge
            if (window.AndroidNotificationBridge) {
              window.AndroidNotificationBridge.onVerificationCheckResult(count, summary);
            }

            return JSON.stringify({
              count: count,
              summary: summary,
              timestamp: new Date().toLocaleTimeString()
            });
          } catch(err) {
            if (window.AndroidNotificationBridge) {
              window.AndroidNotificationBridge.log("Error cek verifikasi: " + err.message);
            }
            return JSON.stringify({ error: err.message });
          }
        };

        // Run immediately after injection
        setTimeout(function() {
          window.__altalindoCheckVerification();
        }, 1500);

        // Schedule periodic check every 10 seconds (10,000 milliseconds)
        window.__altalindoIntervalId = setInterval(function() {
          window.__altalindoCheckVerification();
        }, 10000);

        return "INITIALIZED_EVERY_10_SECONDS";
      })();
    """.trimIndent()
  }

  /**
   * One-shot evaluation script executed every 10 seconds from Kotlin
   * as a backup to guarantee execution even across DOM modifications.
   */
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
