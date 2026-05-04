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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.layout.widthIn
import com.example.mycard.ui.theme.MyCardTheme
import com.example.mycard.ui.theme.SMSReader
import com.example.mycard.SettingsActivity
import com.example.mycard.widget.CardWidgetProvider
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
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }

    // 위젯에서 새로고침 요청 시 데이터 갱신
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh && permissionGranted) {
            groups = SMSReader.readCardApprovalGrouped(context)
            
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

    // SMS 수신 시 앱이 열려있으면 UI 자동 갱신
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    groups = SMSReader.readCardApprovalGrouped(context)
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
    ) { results ->
        val readGranted = results[Manifest.permission.READ_SMS] == true
        permissionGranted = readGranted
        if (readGranted) groups = SMSReader.readCardApprovalGrouped(context)
    }

    LaunchedEffect(Unit) {
        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val receiveGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (readGranted) {
            permissionGranted = true
            groups = SMSReader.readCardApprovalGrouped(context)
        }
        val missing = buildList {
            if (!readGranted) add(Manifest.permission.READ_SMS)
            if (!receiveGranted) add(Manifest.permission.RECEIVE_SMS)
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    // 총액이 변경되면 위젷 업데이트
    LaunchedEffect(groups) {
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
            groups = SMSReader.readCardApprovalGrouped(context)

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
            for (widgetId in widgetIds) {
                CardWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId)
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
        }
    }

    val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date()) }
    val totalCount = groups.sumOf { it.items.size }
    val todayCount = groups.sumOf { group -> group.items.count { it.date.startsWith(todayStr) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("이번 달 카드 내역 ($todayCount/$totalCount)", fontWeight = FontWeight.Bold)
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
                    text = "이번 달 [Web발신] 카드 승인 문자가 없습니다.",
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
                                Text(
                                    text = "이번 달 총 승인",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
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

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column {
                                // 그룹 헤더: ID + 합계 (클릭 가능)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .clickable {
                                            expandedGroups = if (isExpanded) {
                                                expandedGroups - group.id
                                            } else {
                                                expandedGroups + group.id
                                            }
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = group.id,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "%,d원".format(group.totalAmount),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color(0xFF90CAF9),
                                            modifier = Modifier.padding(start = 1.dp).size(20.dp)
                                        )
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
                                                .background(Color(0xFFF0F0F0))
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val halfScreenWidth = (LocalConfiguration.current.screenWidthDp / 2).dp
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.date,
                                                    fontWeight = if (isTodayItem) FontWeight.Bold else FontWeight.Normal,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    modifier = Modifier.widthIn(max = halfScreenWidth),
                                                    text = extractBodyText(item.body),
                                                    fontWeight = if (isTodayItem) FontWeight.Bold else FontWeight.Normal,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "⭐ " + typeText ,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isTodayItem) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCancel)
                                                        MaterialTheme.colorScheme.error
                                                    else
                                                        MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "%,d원".format(displayAmount),

                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isTodayItem) FontWeight.Bold else FontWeight.Normal,
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
