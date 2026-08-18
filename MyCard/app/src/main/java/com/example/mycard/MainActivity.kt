package com.example.mycard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.widthIn
import com.example.mycard.ui.theme.MyCardTheme
import com.example.mycard.sms.SMSReader
import com.example.mycard.SettingsActivity
import com.example.mycard.notif.ManualEntryActivity
import com.example.mycard.notif.NotificationBasedCardActivity
import com.example.mycard.notif.NotificationListActivity
import com.example.mycard.notif.UpdateAction
import com.example.mycard.notif.isListenerPermissionGranted
import com.example.mycard.notif.monthLabel
import com.example.mycard.notif.openListenerSettings
import com.example.mycard.notif.readNotifCardGroups
import com.example.mycard.limit.CardLimitStore
import com.example.mycard.ui.CardAvatar
import com.example.mycard.ui.CardLimitDialog
import com.example.mycard.ui.CardLimitStatus
import com.example.mycard.storage.AppStorage
import com.example.mycard.widget.CardWidgetProvider
import kotlinx.coroutines.launch
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import android.util.Log
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scheduleDailyRefresh()

        // 위젯에서 새로고침 요청 여부 확인
        val shouldRefresh = intent.getBooleanExtra("refresh", false)

        setContent {
            MyCardTheme {
                CardApprovalScreen(shouldRefresh = shouldRefresh)
            }
        }
    }

    private fun scheduleDailyRefresh() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val initialDelay = target.timeInMillis - now.timeInMillis

        val workRequest = PeriodicWorkRequestBuilder<CardRefreshWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "card_refresh_daily",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}

// RCS(JSON) / SMS(plain text) 모두 처리 — 실제 결제 텍스트만 한 줄로 반환
private fun extractBodyText(body: String): String {
    val raw = if (body.trimStart().startsWith("{")) {
        Regex(""""text":"([^"]+)"""").findAll(body)
            .maxByOrNull { it.groupValues[1].length }
            ?.groupValues?.get(1) ?: body
    } else body
    return raw
        .replace("[Web발신]", "")
        .replace(Regex("누적[^\\r\\n\"]*"), "")
        .replace("\\r\\n", " ").replace("\\n", " ").replace("\\r", " ")
        .replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        .replace(Regex(" {2,}"), " ")
        .trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardApprovalScreen(shouldRefresh: Boolean = false) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<SMSReader.SmsGroup>>(emptyList()) }
    var permissionGranted by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    // 0 = 이번 달, -1 = 지난 달. 회전에도 유지한다.
    var monthOffset by rememberSaveable { mutableStateOf(0) }
    // 기본은 펼침. 탭하면 그 카드만 접는다.
    // 기본은 접힘. 펼친 카드는 회전 등 구성 변경에도 유지되어야 한다.
    var expandedGroups by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(setOf<String>()) }
    var limits by remember { mutableStateOf(CardLimitStore.load(context)) }
    var showLimitDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 위젯에서 새로고침 요청 시 데이터 갱신
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh && permissionGranted) {
            groups = readNotifCardGroups(context, monthOffset)

            // 위젷 업데이트
            val grandTotal = groups.sumOf { it.totalAmount }
            val prefs = context.getSharedPreferences("mycard_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("widget_total", grandTotal).apply()

            val groupsJson = StringBuilder("[")
            groups.forEachIndexed { index, group ->
                groupsJson.append("{\"id\":\"${group.id}\",\"total\":${group.totalAmount}}")
                if (index < groups.size - 1) groupsJson.append(",")
            }
            groupsJson.append("]")
            prefs.edit().putString("widget_groups", groupsJson.toString()).apply()

            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val widgetComponentName = android.content.ComponentName(context, CardWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponentName)
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_total)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) limits = CardLimitStore.load(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // SMS 수신 시 앱이 열려있으면 UI 자동 갱신
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    coroutineScope.launch { groups = readNotifCardGroups(context, monthOffset) }
                }
            }
        }
        val filter = IntentFilter(SmsReceiver.ACTION_SMS_UPDATED)
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val readGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        permissionGranted = readGranted
        if (readGranted) coroutineScope.launch { groups = readNotifCardGroups(context, monthOffset) }
    }

    LaunchedEffect(Unit) {
        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val receiveGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (readGranted) {
            permissionGranted = true
            groups = readNotifCardGroups(context, monthOffset)
        }
        val missing = buildList {
            if (!readGranted) add(Manifest.permission.READ_SMS)
            if (!receiveGranted) add(Manifest.permission.RECEIVE_SMS)
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())

        // 이 앱의 집계는 알림 접근 권한 하나에 달려 있다. 꺼지면 에러 없이 그냥 멈추므로 알려준다.
        if (!isListenerPermissionGranted(context)) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "알림 접근 권한이 꺼져 있어 새 결제가 집계되지 않습니다",
                    actionLabel = "설정"
                )
                if (result == SnackbarResult.ActionPerformed) openListenerSettings(context)
            }
        }

        if (!AppStorage.hasAllFilesAccess()) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "외부 저장 권한 필요 (설정에서 토글하세요)",
                    actionLabel = "설정"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    (context as? android.app.Activity)?.let { AppStorage.openAllFilesAccessSettings(it) }
                }
            }
        }
    }

    LaunchedEffect(monthOffset) {
        if (permissionGranted) groups = readNotifCardGroups(context, monthOffset)
    }

    // 총액이 변경되면 위젷 업데이트.
    // 지난 달을 보고 있을 때 위젯을 덮어쓰면 위젯이 과거 달 합계를 표시하게 되므로 이번 달만 반영한다.
    LaunchedEffect(groups, monthOffset) {
        if (monthOffset != 0) return@LaunchedEffect
        val grandTotal = groups.sumOf { it.totalAmount }
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date())
        val totalCount = groups.sumOf { it.items.size }
        val todayCount = groups.sumOf { g -> g.items.count { it.date.startsWith(todayStr) } }
        val prefs = context.getSharedPreferences("mycard_prefs", Context.MODE_PRIVATE)

        prefs.edit()
            .putLong("widget_total", grandTotal)
            .putInt("widget_today_count", todayCount)
            .putInt("widget_total_count", totalCount)
            .apply()

        val groupsJson = StringBuilder("[")
        groups.forEachIndexed { index, group ->
            groupsJson.append("{\"id\":\"${group.id}\",\"total\":${group.totalAmount}}")
            if (index < groups.size - 1) groupsJson.append(",")
        }
        groupsJson.append("]")
        prefs.edit().putString("widget_groups", groupsJson.toString()).apply()

        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val widgetComponentName = android.content.ComponentName(context, CardWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponentName)
        for (widgetId in widgetIds) {
            CardWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId)
        }
    }
    fun isToday(dateString: String): Boolean {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            val itemDate = LocalDateTime.parse(dateString, formatter).toLocalDate()
            val today = LocalDate.now()

            itemDate == today
        } catch (e: Exception) {
            false
        }
    }
    // 데이터 새로고침 함수
    fun refreshData() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            coroutineScope.launch {
                groups = readNotifCardGroups(context, monthOffset)
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
        }
    }

    val totalCount = groups.sumOf { it.items.size }
    val todayCount = groups.sumOf { group -> group.items.count { isToday(it.date) } }

    if (showLimitDialog) {
        val companies = (groups.map { it.id } + limits.keys).distinct().sorted()
        CardLimitDialog(
            companies = companies,
            currentLimits = limits,
            onSave = { updated ->
                CardLimitStore.saveAll(context, updated)
                limits = CardLimitStore.load(context)
            },
            onDismiss = { showLimitDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { monthOffset-- }) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "이전 달"
                            )
                        }
                        Text(
                            text = monthLabel(monthOffset),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { monthOffset++ },
                            enabled = monthOffset < 0
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "다음 달"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFE3F2FD),
                    titleContentColor = Color(0xFF1565C0),
                    actionIconContentColor = Color(0xFF1565C0)
                ),
                actions = {
                    // 새로고침 버튼
                    IconButton(onClick = { refreshData() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "새로고침"
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Text("⋮", style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("설정") },
                                onClick = {
                                    showMenu = false
                                    val intent = android.content.Intent(context, SettingsActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("카드 한도") },
                                onClick = {
                                    showMenu = false
                                    showLimitDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("알림 로그") },
                                onClick = {
                                    showMenu = false
                                    val intent = android.content.Intent(context, NotificationListActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("업데이트") },
                                onClick = {
                                    showMenu = false
                                    coroutineScope.launch {
                                        val result = UpdateAction.rebuildFromRaw(context)
                                        context.sendBroadcast(
                                            Intent(SmsReceiver.ACTION_SMS_UPDATED)
                                                .setPackage(context.packageName)
                                        )
                                        snackbarHostState.showSnackbar(
                                            "재구성 ${result.rebuilt}건 / 파싱 ${result.parsed}건 / " +
                                                "blacklist ${result.skippedByBlacklist}건 / 파싱실패 ${result.skippedByParseFail}건"
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("항목 추가") },
                                onClick = {
                                    showMenu = false
                                    val intent = android.content.Intent(context, ManualEntryActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("알림 기반 보기") },
                                onClick = {
                                    showMenu = false
                                    val intent = android.content.Intent(context, NotificationBasedCardActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            !permissionGranted -> {
                Text(
                    text = "SMS 읽기 권한이 필요합니다.",
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                )
            }

            groups.isEmpty() -> {
                Text(
                    text = "${monthLabel(monthOffset)}에 집계된 카드 내역이 없습니다.",
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                )
            }

            else -> {
                val grandTotal = groups.sumOf { it.totalAmount }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    // 이번 달 전체 합계
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0xFFAAAAAA)),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "총 승인",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (monthOffset == 0) {
                                            "오늘 $todayCount / 전체 ${totalCount}건"
                                        } else {
                                            "전체 ${totalCount}건"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "%,d원".format(grandTotal),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // ID별 그룹 (클릭 시 expand/collapse)
                    items(groups) { group ->
                        val isExpanded = expandedGroups.contains(group.id)
                        val totalItemCount = group.items.size
                        val todayItemCount = group.items.count { isToday(it.date) }
                        val monthlyLimit = limits[group.id]
                        val isOverLimit = monthlyLimit != null && group.totalAmount > monthlyLimit

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (isOverLimit) {
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            } else {
                                CardDefaults.cardColors()
                            },
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column {
                                // 그룹 헤더: ID + 합계 (클릭 가능)
                                // 2행 고정: 1행 = 아이콘·카드명·금액, 2행 = 건수·한도.
                                // 큰 글꼴에서도 줄바꿈되지 않도록 각 Text는 maxLines = 1로 묶는다.
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isOverLimit) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .clickable {
                                            expandedGroups = if (isExpanded) {
                                                expandedGroups - group.id
                                            } else {
                                                expandedGroups + group.id
                                            }
                                        }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CardAvatar(group.id)
                                        Text(
                                            text = group.id,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier
                                                .padding(start = 10.dp)
                                                .weight(1f)
                                        )
                                        Text(
                                            text = "%,d원".format(group.totalAmount),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color(0xFF90CAF9),
                                            modifier = Modifier.padding(start = 2.dp).size(20.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 56.dp, top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "오늘 $todayItemCount / 전체 $totalItemCount",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (monthlyLimit != null) {
                                            CardLimitStatus(monthlyLimit, group.totalAmount)
                                        }
                                    }
                                }

                                // 확장 시에만 문자 리스트 표시
                                if (isExpanded) {
                                    group.items.forEach { item ->
                                        val isCancel = item.amount < 0
                                        val displayAmount =
                                            if (isCancel) -item.amount else item.amount
                                        val typeText = if (isCancel) "취소" else "승인"
                                        val isTodayItem = isToday(item.date)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isCancel) {
                                                        MaterialTheme.colorScheme.errorContainer
                                                            .copy(alpha = 0.4f)
                                                    } else {
                                                        Color(0xFFF0F0F0)
                                                    }
                                                )
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val halfScreenWidth = (LocalConfiguration.current.screenWidthDp / 2).dp
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.date,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    // 1. TextDecoration.Underline 대신 None으로 변경하거나 아예 삭제합니다.
                                                    textDecoration = TextDecoration.None,
                                                    fontWeight = if (isTodayItem) FontWeight.Bold else FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    // 2. 형광펜 효과를 위한 modifier 추가
                                                     modifier = if (isTodayItem) {
                                                        Modifier.background(
                                                            color = Color(0xFFFFFF00).copy(alpha = 0.4f), // 밝은 노란색 + 40% 투명도 (글자가 비쳐보이게)
                                                            shape = RoundedCornerShape(4.dp) // 형광펜 끝을 살짝 둥글게 (생략 가능)
                                                        )
                                                    } else {
                                                        Modifier // 오늘이 아니면 아무 효과 없음
                                                    }
                                                )
                                                Text(
                                                    modifier = Modifier.widthIn(max = halfScreenWidth),
                                                    text = extractBodyText(item.body),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    textDecoration = if (isTodayItem) TextDecoration.Underline else TextDecoration.None,
                                                    fontWeight = if (isTodayItem) FontWeight.Bold else FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = typeText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    textDecoration = if (isTodayItem) TextDecoration.Underline else TextDecoration.None,
                                                    fontWeight = if (isTodayItem) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCancel)
                                                        MaterialTheme.colorScheme.error
                                                    else
                                                        MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "%,d원".format(displayAmount),
                                                    fontWeight = if (isTodayItem) FontWeight.Bold else FontWeight.Normal,
                                                    textDecoration = if (isTodayItem) TextDecoration.Underline else TextDecoration.None,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isCancel)
                                                        MaterialTheme.colorScheme.error
                                                    else
                                                        MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
