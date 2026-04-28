package com.example.mycard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import com.example.mycard.ui.theme.MyCardTheme
import com.example.mycard.sms.SMSReader
import com.example.mycard.SettingsActivity
import com.example.mycard.widget.CardWidgetProvider
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Phase 0: RCS / MaaP 접근 가능성 probe. 결과는 logcat -s SACH 로 확인.
        SMSReader.probeRcsAccess(this)

        // 위젯에서 새로고침 요청 여부 확인
        val shouldRefresh = intent.getBooleanExtra("refresh", false)
        
        setContent {
            MyCardTheme {
                CardApprovalScreen(shouldRefresh = shouldRefresh)
            }
        }
    }
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
        if (isGranted) groups = SMSReader.readCardApprovalGrouped(context)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            permissionGranted = true
            groups = SMSReader.readCardApprovalGrouped(context)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    // 총액이 변경되면 위젷 업데이트
    LaunchedEffect(groups) {
        val grandTotal = groups.sumOf { it.totalAmount }
        val prefs = context.getSharedPreferences("mycard_prefs", Context.MODE_PRIVATE)

        // 총액 저장
        prefs.edit().putLong("widget_total", grandTotal).apply()

        // 그룹별 데이터 JSON으로 저장
        val groupsJson = StringBuilder("[")
        groups.forEachIndexed { index, group ->
            groupsJson.append("{\"id\":\"${group.id}\",\"total\":${group.totalAmount}}")
            if (index < groups.size - 1) groupsJson.append(",")
        }
        groupsJson.append("]")
        prefs.edit().putString("widget_groups", groupsJson.toString()).apply()

        // 위젷 업데이트 요청
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val widgetComponentName = android.content.ComponentName(context, CardWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponentName)
        appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_total)
    }

    // 데이터 새로고침 함수
    fun refreshData() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED
        ) {
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
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("이번 달 카드 승인 내역") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    // 새로고침 버튼
                    IconButton(onClick = {
                        if (permissionGranted) {
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
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                        }
                    }) {
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
                                text = { Text("알림 로그") },
                                onClick = {
                                    showMenu = false
                                    val intent = android.content.Intent(context, com.example.mycard.notif.NotificationListActivity::class.java)
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
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            elevation = CardDefaults.cardElevation(4.dp)
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
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "%,d원".format(grandTotal),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
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
                                        Text(
                                            text = if (isExpanded) " ▲" else " ▼",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
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

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = item.date,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = item.body.replace("[Web발신]", "").replace("누적.*".toRegex(), "").trim(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = typeText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isCancel)
                                                        MaterialTheme.colorScheme.error
                                                    else
                                                        MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = "%,d원".format(displayAmount),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isCancel)
                                                    MaterialTheme.colorScheme.error
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
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
