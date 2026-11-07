package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Sync
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
import com.example.webview.WebAppNotificationBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(
  viewModel: PortalViewModel,
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

  // Request notification permission once if needed on API 33+
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

  // Periodic 10-second verification checker loop (coroutine level backup & guarantee)
  LaunchedEffect(webViewInstance) {
    val webView = webViewInstance ?: return@LaunchedEffect
    while (isActive) {
      delay(10_000L) // Exactly 10 seconds interval
      webView.post {
        webView.evaluateJavascript(PortalScriptHelper.getPeriodicCheckScript()) { _ ->
          // Successfully evaluated periodic verification check
        }
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
          // Trigger test verification directly via JS interface
          webViewInstance?.evaluateJavascript(
            "if(window.AltalindoApp) { window.AltalindoApp.triggerVerification(1, 'Uji Verifikasi Masuk', 'Notifikasi verifikasi terdeteksi setiap 10 detik.'); } else if(window.AndroidNotificationBridge) { window.AndroidNotificationBridge.triggerTestVerification(); }"
          ) { _ -> }
        }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Direct Web Portal WebView
      PortalWebView(
        url = uiState.currentUrl,
        viewModel = viewModel,
        onWebViewReady = { webViewInstance = it },
        modifier = Modifier.fillMaxSize()
      )

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
          errorMessage = uiState.errorMessage ?: "Terjadi kesalahan.",
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
        webViewInstance?.evaluateJavascript(
          "if(window.AndroidNotificationBridge) { window.AndroidNotificationBridge.triggerTestVerification(); }"
        ) { _ -> }
      }
    )
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
          // Live pulse indicator for 10-second check
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
            tint = if (uiState.badgeCount > 0) GoldAmber else Color.White
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
          .background(Color(0xFF10B981)) // Green active dot
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
  onWebViewReady: (WebView) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  val bridge = remember {
    WebAppNotificationBridge(
      context = context,
      onVerificationDetected = { count, title, msg ->
        viewModel.onVerificationDetected(count, title, msg)
      },
      onBadgeUpdated = { count ->
        viewModel.onBadgeUpdated(count)
      },
      onLogReceived = { log ->
        // internal log handling
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
          databaseEnabled = true
          loadWithOverviewMode = true
          useWideViewPort = true
          builtInZoomControls = true
          displayZoomControls = false
          cacheMode = WebSettings.LOAD_DEFAULT
          mediaPlaybackRequiresUserGesture = false
          allowFileAccess = false
          allowContentAccess = false
        }

        // Add JavaScript interfaces (both primary and alias)
        addJavascriptInterface(bridge, WebAppNotificationBridge.BRIDGE_NAME)
        addJavascriptInterface(bridge, WebAppNotificationBridge.ALIAS_NAME)

        // Attach custom clients
        webViewClient = PortalWebViewClient(
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

        webChromeClient = PortalWebChromeClient(
          onProgressChangedCallback = { progress ->
            viewModel.setProgress(progress)
          },
          onTitleReceivedCallback = { title ->
            viewModel.setTitle(title)
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
            text = "Status Verifikasi Altalindo",
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
            text = "Pemeriksaan terakhir: ${uiState.lastCheckTime} (Total ${uiState.totalChecksPerformed} siklus)",
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

      // Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = onTestNotification,
          colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
          modifier = Modifier
            .weight(1f)
            .testTag("test_verification_button")
        ) {
          Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Uji Notifikasi", fontSize = 13.sp)
        }

        if (uiState.badgeCount > 0) {
          OutlinedButton(
            onClick = onClearBadge,
            modifier = Modifier
              .weight(1f)
              .testTag("clear_badge_button")
          ) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Tandai Selesai", fontSize = 13.sp)
          }
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
            .padding(vertical = 24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.outline,
              modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Belum ada verifikasi masuk terdeteksi.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline
            )
            Text(
              text = "Gunakan tombol 'Uji Notifikasi' di atas untuk mencoba.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.outline
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(uiState.verificationHistory, key = { it.id }) { item ->
            VerificationHistoryItem(item)
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
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
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS
    ) == PermissionChecker.PERMISSION_GRANTED
  } else {
    true
  }
}
