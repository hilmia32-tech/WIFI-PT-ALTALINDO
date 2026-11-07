package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NotificationBadgeRed
import com.example.viewmodel.PortalUiState
import com.example.viewmodel.PortalViewModel
import com.example.viewmodel.VerificationEvent
import com.example.webview.PortalScriptHelper
import com.example.webview.PortalWebChromeClient
import com.example.webview.PortalWebViewClient
import com.example.webview.WebAppInterface
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(
  viewModel: PortalViewModel,
  onShowLocalNotification: (message: String, count: Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var showDetailsSheet by remember { mutableStateOf(false) }
  var hasNotificationPermission by remember {
    mutableStateOf(checkNotificationPermission(context))
  }

  // Permission launcher for Android 13+ (POST_NOTIFICATIONS)
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasNotificationPermission = isGranted
  }

  // Request notification permission once at launch if needed
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
      permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  // Handle Android Back button to navigate web history
  BackHandler(enabled = uiState.canGoBack) {
    webViewInstance?.let { webView ->
      if (webView.canGoBack()) {
        webView.goBack()
      }
    }
  }

  // Periodic 10-second verification checker coroutine loop
  LaunchedEffect(webViewInstance) {
    val webView = webViewInstance ?: return@LaunchedEffect
    while (isActive) {
      delay(10_000L) // 10 seconds interval
      webView.post {
        webView.evaluateJavascript(PortalScriptHelper.getPeriodicCheckScript()) { _ -> }
      }
      viewModel.recordCheckCycle()
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("portal_scaffold"),
    topBar = {
      PortalTopAppBar(
        uiState = uiState,
        onRefreshClicked = { webViewInstance?.reload() },
        onBackClicked = {
          if (webViewInstance?.canGoBack() == true) webViewInstance?.goBack()
        },
        onForwardClicked = {
          if (webViewInstance?.canGoForward() == true) webViewInstance?.goForward()
        },
        onBadgeClicked = { showDetailsSheet = true },
        onTestTriggerClicked = {
          // Trigger the exact updateBadge logic directly
          onShowLocalNotification("Ada 1 pembayaran menunggu verifikasi!", 1)
        }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Notification permission warning banner if permission is denied
        if (!hasNotificationPermission) {
          PermissionAlertBanner(
            onRequestPermission = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                openAppNotificationSettings(context)
              }
            }
          )
        }

        // Direct Web Portal WebView
        PortalWebView(
          url = uiState.currentUrl,
          viewModel = viewModel,
          onShowLocalNotification = onShowLocalNotification,
          onWebViewReady = { webViewInstance = it },
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        )
      }

      // Top progress bar during page load
      if (uiState.isLoading && uiState.progress < 100) {
        LinearProgressIndicator(
          progress = { uiState.progress / 100f },
          modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .align(Alignment.TopCenter)
            .testTag("loading_progress_bar"),
          color = GoldAmber,
          trackColor = Color.Transparent
        )
      }

      // Offline / Error banner with retry
      AnimatedVisibility(
        visible = uiState.errorMessage != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(16.dp)
      ) {
        ErrorCard(
          errorMessage = uiState.errorMessage ?: "Terjadi kesalahan koneksi.",
          onRetry = {
            viewModel.setError(null)
            webViewInstance?.reload()
          }
        )
      }
    }
  }

  // Verification details and logs bottom sheet
  if (showDetailsSheet) {
    VerificationDetailsSheet(
      uiState = uiState,
      onDismiss = { showDetailsSheet = false },
      onClearBadge = {
        viewModel.clearBadge()
        showDetailsSheet = false
      },
      onTestNotification = {
        // Test notification, phone ring, and badge update
        onShowLocalNotification("Ada 1 pembayaran menunggu verifikasi!", 1)
      }
    )
  }
}

@Composable
private fun PermissionAlertBanner(onRequestPermission: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = Color(0xFFFEF3C7) // Light amber
    ),
    shape = RoundedCornerShape(0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("permission_banner")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        tint = Color(0xFFB45309),
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Izin notifikasi belum aktif. Aktifkan agar HP berdering saat ada verifikasi masuk.",
        fontSize = 11.sp,
        color = Color(0xFF78350F),
        modifier = Modifier.weight(1f)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Button(
        onClick = onRequestPermission,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
        shape = RoundedCornerShape(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(28.dp)
      ) {
        Text("Aktifkan", fontSize = 11.sp, color = Color.White)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortalTopAppBar(
  uiState: PortalUiState,
  onRefreshClicked: () -> Unit,
  onBackClicked: () -> Unit,
  onForwardClicked: () -> Unit,
  onBadgeClicked: () -> Unit,
  onTestTriggerClicked: () -> Unit
) {
  TopAppBar(
    title = {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "Altalindo",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            ),
            color = Color.White
          )
          Spacer(modifier = Modifier.width(8.dp))
          MonitoringPill(totalChecks = uiState.totalChecksPerformed)
        }
        Text(
          text = "altalindo.blogspot.com",
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
          color = Color.White.copy(alpha = 0.75f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    },
    navigationIcon = {
      if (uiState.canGoBack) {
        IconButton(
          onClick = onBackClicked,
          modifier = Modifier.testTag("web_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Kembali",
            tint = Color.White
          )
        }
      }
    },
    actions = {
      if (uiState.canGoForward) {
        IconButton(
          onClick = onForwardClicked,
          modifier = Modifier.testTag("web_forward_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Maju",
            tint = Color.White
          )
        }
      }

      // Refresh Button
      IconButton(
        onClick = onRefreshClicked,
        modifier = Modifier.testTag("web_refresh_button")
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Muat Ulang Halaman",
          tint = Color.White
        )
      }

      // Quick Sound & Notification Test Trigger
      IconButton(
        onClick = onTestTriggerClicked,
        modifier = Modifier.testTag("quick_sound_test_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.VolumeUp,
          contentDescription = "Uji Suara & Notifikasi",
          tint = GoldAmber
        )
      }

      // Notification Bell with Badge counter
      IconButton(
        onClick = onBadgeClicked,
        modifier = Modifier.testTag("notification_badge_button")
      ) {
        BadgedBox(
          badge = {
            if (uiState.badgeCount > 0) {
              Badge(
                containerColor = NotificationBadgeRed,
                contentColor = Color.White,
                modifier = Modifier.testTag("badge_counter")
              ) {
                Text(
                  text = if (uiState.badgeCount > 99) "99+" else uiState.badgeCount.toString(),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        ) {
          Icon(
            imageVector = if (uiState.badgeCount > 0) {
              Icons.Default.NotificationsActive
            } else {
              Icons.Default.Notifications
            },
            contentDescription = "Notifikasi Verifikasi",
            tint = if (uiState.badgeCount > 0) NotificationBadgeRed else Color.White
          )
        }
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = NavyPrimary,
      titleContentColor = Color.White,
      actionIconContentColor = Color.White
    )
  )
}

@Composable
private fun MonitoringPill(totalChecks: Int) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color.White.copy(alpha = 0.15f),
    modifier = Modifier.testTag("monitoring_pill")
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
      Box(
        modifier = Modifier
          .size(6.dp)
          .clip(CircleShape)
          .background(Color(0xFF10B981))
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = "Auto 10d",
        color = Color(0xFFE2E8F0),
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PortalWebView(
  url: String,
  viewModel: PortalViewModel,
  onShowLocalNotification: (message: String, count: Int) -> Unit,
  onWebViewReady: (WebView) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  // Exact WebAppInterface with bridge name "AndroidBridge" as requested by user
  val webAppInterface = remember {
    WebAppInterface(
      context = context,
      onBadgeChanged = { count ->
        viewModel.onBadgeUpdated(count)
      },
      onVerificationReceived = { count, title, msg ->
        viewModel.onVerificationDetected(count, title, msg)
      }
    )
  }

  AndroidView(
    factory = { ctx ->
      WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )

        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          setSupportMultipleWindows(true)
          javaScriptCanOpenWindowsAutomatically = true
          loadWithOverviewMode = true
          useWideViewPort = true
          builtInZoomControls = true
          displayZoomControls = false
          cacheMode = WebSettings.LOAD_DEFAULT
          mediaPlaybackRequiresUserGesture = false
          allowFileAccess = false
          allowContentAccess = false
        }

        // Register exact JavascriptInterface requested by the user:
        // webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")
        addJavascriptInterface(webAppInterface, "AndroidBridge")
        addJavascriptInterface(webAppInterface, "AndroidNotificationBridge")

        // Attach custom clients with WhatsApp external URL support
        val customClient = PortalWebViewClient(
          context = ctx,
          onLoadingStateChanged = { loading ->
            viewModel.setLoading(loading)
            viewModel.setNavigationState(canGoBack(), canGoForward())
          },
          onErrorReceived = { error ->
            viewModel.setError(error)
          },
          onPageFinishedHook = { _ ->
            viewModel.setNavigationState(canGoBack(), canGoForward())
          }
        )
        webViewClient = customClient

        webChromeClient = PortalWebChromeClient(
          onProgressChangedCallback = { progress ->
            viewModel.setProgress(progress)
          },
          onTitleReceivedCallback = { title ->
            viewModel.setTitle(title)
          },
          onExternalUrlHandler = { targetUrl ->
            customClient.handleUrl(this, targetUrl)
          }
        )

        loadUrl(url)
        onWebViewReady(this)
      }
    },
    update = { webView ->
      viewModel.setNavigationState(webView.canGoBack(), webView.canGoForward())
    },
    modifier = modifier.testTag("altalindo_webview")
  )
}

@Composable
private fun ErrorCard(
  errorMessage: String,
  onRetry: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer
    ),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("error_card")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = "Kesalahan",
        tint = MaterialTheme.colorScheme.error
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = errorMessage,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.weight(1f)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Button(
        onClick = onRetry,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error
        ),
        modifier = Modifier.testTag("retry_button")
      ) {
        Text("Coba Lagi", color = Color.White)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerificationDetailsSheet(
  uiState: PortalUiState,
  onDismiss: () -> Unit,
  onClearBadge: () -> Unit,
  onTestNotification: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = Modifier.testTag("verification_bottom_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = null,
            tint = NavyPrimary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Status Verifikasi & Notifikasi",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("close_sheet_button")
        ) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Status Card
      Card(
        colors = CardDefaults.cardColors(
          containerColor = NavyPrimary.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Pemantau Otomatis: Aktif (Setiap 10 Detik)",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Pengecekan: ${uiState.lastCheckTime} (Total ${uiState.totalChecksPerformed} siklus)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Badge verifikasi saat ini: ",
              style = MaterialTheme.typography.bodyMedium
            )
            Text(
              text = "${uiState.badgeCount}",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (uiState.badgeCount > 0) NotificationBadgeRed else MaterialTheme.colorScheme.primary
              )
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Test Notification Button with phone sound and vibration
      Button(
        onClick = onTestNotification,
        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("test_verification_button")
      ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Uji Suara & Notifikasi (Ada 1 Pembayaran)", fontSize = 14.sp)
      }

      if (uiState.badgeCount > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = onClearBadge,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("clear_badge_button")
        ) {
          Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Reset Badge Angka Menjadi 0", fontSize = 14.sp)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider()
      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Riwayat Deteksi Verifikasi",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
      )

      Spacer(modifier = Modifier.height(8.dp))

      if (uiState.verificationHistory.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.outline,
              modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Belum ada verifikasi masuk terdeteksi.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline
            )
            Text(
              text = "Klik tombol 'Uji Suara & Notifikasi' di atas untuk mencoba.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.outline
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(uiState.verificationHistory, key = { it.id }) { item ->
            VerificationHistoryItem(item)
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun VerificationHistoryItem(item: VerificationEvent) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ),
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("history_item_${item.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalAlignment = Alignment.Top
    ) {
      Box(
        modifier = Modifier
          .size(24.dp)
          .clip(CircleShape)
          .background(NotificationBadgeRed),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "${item.count}",
          color = Color.White,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = item.timeFormatted,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = item.message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun checkNotificationPermission(context: Context): Boolean {
  val areEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
  if (!areEnabled) return false

  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS
    ) == PermissionChecker.PERMISSION_GRANTED
  } else {
    true
  }
}

private fun openAppNotificationSettings(context: Context) {
  try {
    val intent = Intent().apply {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
      } else {
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        data = Uri.fromParts("package", context.packageName, null)
      }
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
  } catch (_: Exception) {}
}
