package com.example.arintabletusage

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.arintabletusage.data.AppPreferences
import com.example.arintabletusage.permissions.PermissionHelper
import com.example.arintabletusage.report.UsageReportBuilder
import com.example.arintabletusage.schedule.ReportScheduler
import com.example.arintabletusage.schedule.UsageCheckScheduler
import com.example.arintabletusage.ui.theme.ArinTabletUsageTheme
import com.example.arintabletusage.worker.SendReportWorker
import java.util.Calendar

class MainActivity : ComponentActivity() {
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 매시간 사용 시간을 체크하는 백그라운드 작업을 예약한다. 이미 예약돼 있으면 그대로 유지된다.
        UsageCheckScheduler.schedule(this)
        setContent {
            ArinTabletUsageTheme {
                val context = LocalContext.current
                var menuExpanded by remember { mutableStateOf(false) }
                var currentUsageDialogText by remember { mutableStateOf<String?>(null) }

                Scaffold(
                    modifier = Modifier.fillMaxWidth(),
                    topBar = {
                        TopAppBar(
                            title = { Text("태블릿 사용시간 리포트") },
                            actions = {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Text("⋮", style = MaterialTheme.typography.titleLarge)
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("현재 사용시간 보기") },
                                        onClick = {
                                            menuExpanded = false
                                            if (!PermissionHelper.hasUsageAccess(context)) {
                                                Toast.makeText(
                                                    context,
                                                    "먼저 사용 기록 접근 권한을 허용해주세요.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@DropdownMenuItem
                                            }
                                            val report = UsageReportBuilder.buildTodayReport(context)
                                            currentUsageDialogText = UsageReportBuilder.buildReportText(report)
                                        }
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    ReportSettingsScreen(modifier = Modifier.padding(innerPadding))
                }

                currentUsageDialogText?.let { text ->
                    AlertDialog(
                        onDismissRequest = { currentUsageDialogText = null },
                        confirmButton = {
                            TextButton(onClick = { currentUsageDialogText = null }) {
                                Text("닫기")
                            }
                        },
                        title = { Text("현재 사용시간") },
                        text = {
                            Text(
                                text = text,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ReportSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    var recipientEmail by remember { mutableStateOf(prefs.recipientEmail) }
    var senderEmail by remember { mutableStateOf(prefs.senderEmail) }
    var senderPassword by remember { mutableStateOf(prefs.senderPassword) }
    var smtpHost by remember { mutableStateOf(prefs.smtpHost) }
    var smtpPort by remember { mutableStateOf(prefs.smtpPort.toString()) }
    var hour by remember { mutableStateOf(prefs.scheduleHour) }
    var minute by remember { mutableStateOf(prefs.scheduleMinute) }
    var scheduleEnabled by remember { mutableStateOf(prefs.scheduleEnabled) }
    var warningThresholdHours by remember { mutableStateOf(prefs.warningThresholdHours.toString()) }
    var lastResult by remember { mutableStateOf(prefs.lastSendResult) }

    var hasUsageAccess by remember { mutableStateOf(PermissionHelper.hasUsageAccess(context)) }
    var canExactAlarm by remember { mutableStateOf(ReportScheduler.canScheduleExactAlarms(context)) }
    var hasOverlayPermission by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }

    // 설정 화면(사용 기록 접근/정확한 알람)에 다녀온 뒤 화면으로 복귀하면 권한 상태를 다시 읽는다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = PermissionHelper.hasUsageAccess(context)
                canExactAlarm = ReportScheduler.canScheduleExactAlarms(context)
                hasOverlayPermission = PermissionHelper.hasOverlayPermission(context)
                lastResult = prefs.lastSendResult
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("리포트 수신 설정", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = recipientEmail,
                    onValueChange = { recipientEmail = it },
                    label = { Text("리포트 받을 이메일") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("발송 시각: %02d:%02d".format(hour, minute))
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> hour = h; minute = m },
                            hour,
                            minute,
                            true
                        ).show()
                    }) {
                        Text("시간 선택")
                    }
                }

                OutlinedTextField(
                    value = warningThresholdHours,
                    onValueChange = { warningThresholdHours = it.filter { c -> c.isDigit() } },
                    label = { Text("경고 기준 시간 (하루 총 사용, 시간 단위)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("발신 계정 설정 (SMTP)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "예약된 시각에 사람 개입 없이 자동으로 메일을 보내려면, 실제로 로그인해서 " +
                        "메일을 발송할 '보내는 사람' 계정이 필요합니다. Gmail 사용 시 구글 계정의 " +
                        "2단계 인증을 켜고 '앱 비밀번호'를 발급받아 아래에 입력하세요.",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = senderEmail,
                    onValueChange = { senderEmail = it },
                    label = { Text("발신 이메일 (예: xxx@gmail.com)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = senderPassword,
                    onValueChange = { senderPassword = it },
                    label = { Text("앱 비밀번호 (App Password)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = smtpHost,
                        onValueChange = { smtpHost = it },
                        label = { Text("SMTP 서버") },
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = smtpPort,
                        onValueChange = { smtpPort = it.filter { c -> c.isDigit() } },
                        label = { Text("포트") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("권한 상태", style = MaterialTheme.typography.titleMedium)

                PermissionRow(
                    label = "사용 기록 접근 권한",
                    granted = hasUsageAccess,
                    buttonText = "설정 열기"
                ) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }

                PermissionRow(
                    label = "정확한 알람 권한",
                    granted = canExactAlarm,
                    buttonText = "설정 열기"
                ) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }

                PermissionRow(
                    label = "알림 권한",
                    granted = hasNotificationPermission,
                    buttonText = "허용 요청"
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                PermissionRow(
                    label = "다른 앱 위에 표시 권한 (경고 오버레이용)",
                    granted = hasOverlayPermission,
                    buttonText = "설정 열기"
                ) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val trimmedRecipient = recipientEmail.trim()
                    val trimmedSender = senderEmail.trim()
                    if (!Patterns.EMAIL_ADDRESS.matcher(trimmedRecipient).matches()) {
                        Toast.makeText(context, "받는 사람 이메일 형식이 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!Patterns.EMAIL_ADDRESS.matcher(trimmedSender).matches()) {
                        Toast.makeText(context, "발신 이메일 형식이 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (senderPassword.isBlank()) {
                        Toast.makeText(context, "발신 계정의 앱 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val port = smtpPort.toIntOrNull() ?: 587

                    prefs.recipientEmail = trimmedRecipient
                    prefs.senderEmail = trimmedSender
                    prefs.senderPassword = senderPassword
                    prefs.smtpHost = smtpHost.trim().ifBlank { "smtp.gmail.com" }
                    prefs.smtpPort = port
                    prefs.scheduleHour = hour
                    prefs.scheduleMinute = minute
                    prefs.scheduleEnabled = true
                    scheduleEnabled = true
                    prefs.warningThresholdHours = warningThresholdHours.toIntOrNull()
                        ?: AppPreferences.DEFAULT_WARNING_THRESHOLD_HOURS

                    ReportScheduler.schedule(context, hour, minute)
                    val next = Calendar.getInstance().apply {
                        timeInMillis = ReportScheduler.nextTriggerTime(hour, minute)
                    }
                    Toast.makeText(
                        context,
                        "저장 완료. 다음 발송: %02d-%02d %02d:%02d".format(
                            next.get(Calendar.MONTH) + 1,
                            next.get(Calendar.DAY_OF_MONTH),
                            hour,
                            minute
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("저장하고 매일 예약하기")
            }

            OutlinedButton(
                onClick = {
                    ReportScheduler.cancel(context)
                    prefs.scheduleEnabled = false
                    scheduleEnabled = false
                    Toast.makeText(context, "예약을 취소했습니다.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("예약 취소")
            }

            OutlinedButton(
                onClick = {
                    if (!hasUsageAccess) {
                        Toast.makeText(context, "먼저 사용 기록 접근 권한을 허용해주세요.", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    // 테스트 발송은 화면에 입력된 최신 값을 그대로 사용하도록 먼저 저장한다.
                    prefs.recipientEmail = recipientEmail.trim()
                    prefs.senderEmail = senderEmail.trim()
                    prefs.senderPassword = senderPassword
                    prefs.smtpHost = smtpHost.trim().ifBlank { "smtp.gmail.com" }
                    prefs.smtpPort = smtpPort.toIntOrNull() ?: 587

                    val request = OneTimeWorkRequestBuilder<SendReportWorker>().build()
                    WorkManager.getInstance(context).enqueue(request)
                    Toast.makeText(context, "테스트 발송을 시작했습니다.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("지금 테스트 발송")
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("현재 상태", style = MaterialTheme.typography.titleMedium)
                Text(if (scheduleEnabled) "예약: 켜짐 (매일 %02d:%02d)".format(hour, minute) else "예약: 꺼짐")
                Text("경고 기준: 하루 총 사용 ${warningThresholdHours.ifBlank { "0" }}시간 초과")
                Text("마지막 발송 결과: ${lastResult.ifBlank { "아직 없음" }}")
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text("$label: ${if (granted) "허용됨" else "필요함"}")
        if (!granted) {
            OutlinedButton(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}
