package com.example.mycard.notif

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.mycard.SmsReceiver
import com.example.mycard.parser.CardFilter
import com.example.mycard.parser.CardFilterStore
import com.example.mycard.parser.Match
import com.example.mycard.ui.theme.MyCardTheme
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManualEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyCardTheme {
                ManualEntryScreen(onClose = { finish() })
            }
        }
    }
}

private const val MANUAL_PKG = "com.example.mycard.manual"
private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEntryScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var companies by remember { mutableStateOf<List<String>>(emptyList()) }
    var companyToFilters by remember { mutableStateOf<Map<String, List<CardFilter>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val filters = withContext(Dispatchers.IO) {
            CardFilterStore.load(context).filters
        }
        companyToFilters = filters.groupBy { it.cardCompany }
        companies = companyToFilters.keys.sorted()
    }

    var selectedCompany by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    var isCancel by remember { mutableStateOf(false) }
    var memo by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("항목 추가", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFE3F2FD),
                    titleContentColor = Color(0xFF1565C0)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CardCompanyDropdown(
                companies = companies,
                selected = selectedCompany,
                onSelect = { selectedCompany = it }
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { v ->
                    amountText = v.filter { it.isDigit() }
                },
                label = { Text("금액") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("구분:", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "승인",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (!isCancel) FontWeight.Bold else FontWeight.Normal,
                    color = if (!isCancel) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { isCancel = false }
                )
                Text(
                    text = "취소",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCancel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCancel) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { isCancel = true }
                )
            }

            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("비고 (가맹점/메모)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "시간: 저장 시점으로 자동 기록",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClose, enabled = !saving) {
                    Text("취소")
                }
                Box(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val company = selectedCompany
                        val amountLong = amountText.toLongOrNull()
                        when {
                            company.isNullOrBlank() -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("카드를 선택하세요")
                            }
                            amountLong == null || amountLong <= 0L -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("금액을 입력하세요")
                            }
                            else -> {
                                saving = true
                                coroutineScope.launch {
                                    val result = saveManualEntry(
                                        context = context,
                                        company = company,
                                        filtersForCompany = companyToFilters[company].orEmpty(),
                                        amount = amountLong,
                                        isCancel = isCancel,
                                        memo = memo.trim()
                                    )
                                    saving = false
                                    snackbarHostState.showSnackbar(
                                        "추가됨 — 재구성 ${result.rebuilt}건 / 파싱 ${result.parsed}건"
                                    )
                                    onClose()
                                }
                            }
                        }
                    },
                    enabled = !saving
                ) {
                    Text(if (saving) "저장 중..." else "저장")
                }
            }
        }
    }
}

@Composable
private fun CardCompanyDropdown(
    companies: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("카드 종류") },
            trailingIcon = {
                Text(
                    text = if (expanded) "▲" else "▼",
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (companies.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("등록된 카드 필터가 없습니다") },
                    onClick = { expanded = false },
                    enabled = false
                )
            }
            companies.forEach { company ->
                DropdownMenuItem(
                    text = { Text(company) },
                    onClick = {
                        onSelect(company)
                        expanded = false
                    }
                )
            }
        }
    }
}

private suspend fun saveManualEntry(
    context: android.content.Context,
    company: String,
    filtersForCompany: List<CardFilter>,
    amount: Long,
    isCancel: Boolean,
    memo: String
): RebuildResult = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val signedAmount = if (isCancel) -amount else amount
    val typeText = if (isCancel) "취소" else "승인"
    val merchantText = if (memo.isBlank()) "(메모 없음)" else memo
    val whenText = DATE_FORMAT.format(Date(now))

    val preferredType = if (isCancel) Match.TYPE_CANCEL else Match.TYPE_APPROVAL
    val filterId = filtersForCompany.firstOrNull { it.match.type == preferredType }?.id
        ?: filtersForCompany.firstOrNull()?.id

    val title = "[수동입력] $company"
    val text = "$company $typeText %,d원 $merchantText".format(amount)
    val bigText = "$text @ $whenText"

    val obj = JsonObject().apply {
        addProperty("ts", now)
        addProperty("pkg", MANUAL_PKG)
        addProperty("title", title)
        addProperty("text", text)
        addProperty("bigText", bigText)
        addProperty("subText", "")
        addProperty("category", "")
        addProperty("channelId", "manual")
        addProperty("rawExtras", "{}")
        addProperty("amount", signedAmount)
        addProperty("merchant", merchantText)
        if (filterId != null) addProperty("filterId", filterId)
        addProperty("parsedAt", now)
    }

    RawDump.appendObject(context, obj)
    val result = UpdateAction.rebuildFromRaw(context)

    context.sendBroadcast(
        Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(context.packageName)
    )
    SmsReceiver.refreshAndNotify(context)

    result
}
