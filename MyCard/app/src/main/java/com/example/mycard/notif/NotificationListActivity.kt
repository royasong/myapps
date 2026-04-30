package com.example.mycard.notif

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycard.notif.db.NotificationDatabase
import com.example.mycard.notif.db.NotificationEntity
import com.example.mycard.ui.theme.MyCardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyCardTheme { NotificationListScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val dao = remember { NotificationDatabase.get(context).notificationDao() }
    val logs by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var permissionGranted by remember { mutableStateOf(isListenerPermissionGranted(context)) }
    var batteryOptIgnored by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var whitelist by remember { mutableStateOf(Whitelist.all(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = isListenerPermissionGranted(context)
                batteryOptIgnored = isIgnoringBatteryOptimizations(context)
                whitelist = Whitelist.all(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun deleteLog(log: NotificationEntity) {
        scope.launch(Dispatchers.IO) {
            dao.deleteById(log.id)
            RawDump.removeLineById(context, log.id)
        }
    }

    fun toggleWhitelist(pkg: String) {
        scope.launch(Dispatchers.IO) {
            if (Whitelist.contains(context, pkg)) Whitelist.remove(context, pkg)
            else Whitelist.add(context, pkg)
            val refreshed = Whitelist.all(context)
            withContext(Dispatchers.Main) { whitelist = refreshed }
        }
    }

    fun addToBlacklist(pkg: String) {
        scope.launch(Dispatchers.IO) {
            Blacklist.add(context, pkg)
            if (Whitelist.contains(context, pkg)) Whitelist.remove(context, pkg)
            dao.deleteByPkg(pkg)
            RawDump.removeLinesByPkg(context, pkg)
            val refreshed = Whitelist.all(context)
            withContext(Dispatchers.Main) { whitelist = refreshed }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("알림 로그") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        permissionGranted = isListenerPermissionGranted(context)
                        batteryOptIgnored = isIgnoringBatteryOptimizations(context)
                        whitelist = Whitelist.all(context)
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            PermissionBanner(
                granted = permissionGranted,
                count = logs.size,
                lastTs = logs.firstOrNull()?.ts,
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )
            if (!batteryOptIgnored) {
                BatteryOptimizationBanner(
                    ignored = batteryOptIgnored,
                    onRequestExclude = { requestIgnoreBatteryOptimizations(context) },
                    onOpenAppBattery = { openAppBatterySettings(context) }
                )
            }
            when {
                logs.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (permissionGranted)
                                "아직 알림이 없습니다.\n알림이 도착하면 여기에 표시됩니다."
                            else
                                "알림 접근 권한을 켜주세요.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(items = logs, key = { it.id }) { log ->
                            NotificationCard(
                                log = log,
                                isWhitelisted = log.pkg in whitelist,
                                onDelete = { deleteLog(log) },
                                onToggleWhitelist = { toggleWhitelist(log.pkg) },
                                onAddBlacklist = { addToBlacklist(log.pkg) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationCard(
    log: NotificationEntity,
    isWhitelisted: Boolean,
    onDelete: () -> Unit,
    onToggleWhitelist: () -> Unit,
    onAddBlacklist: () -> Unit
) {
    var expanded by rememberSaveable(log.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val (appLabel, appIcon) = remember(log.pkg) { resolveApp(context, log.pkg) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { menuOpen = true }
            ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    appIcon?.let {
                        Image(
                            bitmap = it.toBitmap(width = 64, height = 64).asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        appLabel,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    if (isWhitelisted) {
                        WhitelistBadge()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        formatTime(log.ts),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    log.title.ifEmpty { "(제목 없음)" },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                val body = log.bigText.ifEmpty { log.text }
                Text(
                    body.ifEmpty { "(본문 없음)" },
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    color = Color.DarkGray
                )
                Spacer(Modifier.height(4.dp))
                Text(log.pkg, fontSize = 10.sp, color = Color.Gray)
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    if (log.subText.isNotEmpty()) {
                        Text("subText: ${log.subText}", fontSize = 11.sp, color = Color.DarkGray)
                    }
                    if (log.category.isNotEmpty()) {
                        Text("category: ${log.category}", fontSize = 11.sp, color = Color.DarkGray)
                    }
                    if (log.channelId.isNotEmpty()) {
                        Text("channelId: ${log.channelId}", fontSize = 11.sp, color = Color.DarkGray)
                    }
                    Text("ts: ${log.ts}", fontSize = 11.sp, color = Color.DarkGray)
                    Text("id: ${log.id}", fontSize = 11.sp, color = Color.DarkGray)
                    if (log.rawExtras.isNotEmpty() && log.rawExtras != "{}") {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "rawExtras:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                        Text(log.rawExtras, fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(if (isWhitelisted) "화이트리스트 제거" else "화이트리스트 추가")
                    },
                    onClick = {
                        menuOpen = false
                        onToggleWhitelist()
                    }
                )
                DropdownMenuItem(
                    text = { Text("블랙리스트 추가") },
                    onClick = {
                        menuOpen = false
                        onAddBlacklist()
                    }
                )
                DropdownMenuItem(
                    text = { Text("삭제") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun WhitelistBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "✓ 화이트",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PermissionBanner(
    granted: Boolean,
    count: Int,
    lastTs: Long?,
    onOpenSettings: () -> Unit
) {
    val bgColor = if (granted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val dotColor = if (granted) Color(0xFF43A047) else Color(0xFFE53935)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (granted) {
                Text("권한 ON · 적재 ${count}건", fontWeight = FontWeight.Medium)
                lastTs?.let {
                    Text(
                        "마지막 도착: ${formatTime(it)}",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            } else {
                Text("알림 접근 권한이 꺼져있습니다", fontWeight = FontWeight.Bold)
                Text(
                    "설정 → 알림 → 알림 접근 → MyCard 허용",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            Button(onClick = onOpenSettings) { Text("설정 열기") }
        }
    }
}

@Composable
private fun BatteryOptimizationBanner(
    ignored: Boolean,
    onRequestExclude: () -> Unit,
    onOpenAppBattery: () -> Unit
) {
    val bgColor = if (ignored) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val dotColor = if (ignored) Color(0xFF43A047) else Color(0xFFFB8C00)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (ignored) {
                Text("배터리 최적화 제외됨", fontWeight = FontWeight.Medium)
                Text(
                    "백그라운드 종료가 줄어듭니다.",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            } else {
                Text("배터리 최적화 적용 중", fontWeight = FontWeight.Bold)
                Text(
                    "백그라운드에서 종료될 수 있습니다.",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }
        }
        if (!ignored) {
            Spacer(Modifier.width(8.dp))
            Button(onClick = onRequestExclude) { Text("제외 요청") }
        } else {
            Spacer(Modifier.width(8.dp))
            Button(onClick = onOpenAppBattery) { Text("앱 배터리") }
        }
    }
}

private fun isListenerPermissionGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun openAppBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

private fun resolveApp(context: Context, pkg: String): Pair<String, Drawable?> {
    val pm = context.packageManager
    return try {
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString() to pm.getApplicationIcon(info)
    } catch (e: PackageManager.NameNotFoundException) {
        pkg to null
    }
}

private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private fun formatTime(ts: Long): String = timeFormatter.format(Date(ts))
