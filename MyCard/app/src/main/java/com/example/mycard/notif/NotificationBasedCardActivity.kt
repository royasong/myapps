package com.example.mycard.notif

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycard.SmsReceiver
import com.example.mycard.notif.db.NotificationDatabase
import com.example.mycard.notif.db.NotificationEntity
import com.example.mycard.parser.CardFilterStore
import com.example.mycard.ui.theme.MyCardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class NotificationBasedCardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyCardTheme {
                NotificationBasedCardScreen()
            }
        }
    }
}

private data class CardSummaryGroup(
    val cardCompany: String,
    val totalAmount: Long,
    val items: List<NotificationEntity>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotificationBasedCardScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { NotificationDatabase.get(context).notificationDao() }

    val startOfMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val parsed by dao.observeParsedSince(startOfMonth)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val groups = remember(parsed) {
        groupByCompany(context, parsed)
    }

    var expandedGroups by remember { mutableStateOf(setOf<String>()) }

    // 다이얼로그 상태
    var selectedItem by remember { mutableStateOf<NotificationEntity?>(null) }
    var editingItem by remember { mutableStateOf<NotificationEntity?>(null) }
    var deletingItem by remember { mutableStateOf<NotificationEntity?>(null) }
    var processing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("이번 달 알림 기반 카드 승인") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (groups.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                EmptyStateCard()
            }
            return@Scaffold
        }

        val grandTotal = groups.sumOf { it.totalAmount }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
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

            items(groups) { group ->
                val isExpanded = expandedGroups.contains(group.cardCompany)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .clickable {
                                    expandedGroups = if (isExpanded) {
                                        expandedGroups - group.cardCompany
                                    } else {
                                        expandedGroups + group.cardCompany
                                    }
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.cardCompany,
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

                        if (isExpanded) {
                            group.items.forEach { item ->
                                val amount = item.amount ?: 0L
                                val isCancel = amount < 0
                                val displayAmount = abs(amount)
                                val typeText = if (isCancel) "취소" else "승인"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = { selectedItem = item }
                                        )
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = formatTs(item.ts),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = item.merchant ?: item.text.ifEmpty { item.title },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = typeText,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isCancel)
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.primary
                                        )
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
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // 액션 선택 다이얼로그 (롱클릭 후)
    selectedItem?.let { item ->
        val label = item.merchant?.takeIf { it.isNotBlank() }
            ?: item.text.ifEmpty { item.title }
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(label, maxLines = 2) },
            text = {
                Column {
                    TextButton(
                        onClick = { editingItem = item; selectedItem = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("수정", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = { deletingItem = item; selectedItem = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "삭제",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedItem = null }) { Text("닫기") }
            }
        )
    }

    // 수정 다이얼로그
    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            processing = processing,
            onSave = { amount, merchant, isCancel ->
                processing = true
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val signed = if (isCancel) -amount else amount
                        RawDump.updateByTs(context, item.ts, signed, merchant)
                        UpdateAction.rebuildFromRaw(context)
                        SmsReceiver.refreshAndNotify(context)
                    } finally {
                        withContext(Dispatchers.Main) {
                            processing = false
                            editingItem = null
                        }
                    }
                }
            },
            onDismiss = { if (!processing) editingItem = null }
        )
    }

    // 삭제 확인 다이얼로그
    deletingItem?.let { item ->
        val label = item.merchant?.takeIf { it.isNotBlank() }
            ?: item.text.ifEmpty { item.title }
        AlertDialog(
            onDismissRequest = { if (!processing) deletingItem = null },
            title = { Text("삭제 확인") },
            text = { Text("\"$label\" 항목을 삭제할까요?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        processing = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                RawDump.removeByTs(context, item.ts)
                                UpdateAction.rebuildFromRaw(context)
                                SmsReceiver.refreshAndNotify(context)
                            } finally {
                                withContext(Dispatchers.Main) {
                                    processing = false
                                    deletingItem = null
                                }
                            }
                        }
                    },
                    enabled = !processing
                ) {
                    Text(
                        if (processing) "삭제 중..." else "삭제",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }, enabled = !processing) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun EditItemDialog(
    item: NotificationEntity,
    processing: Boolean,
    onSave: (amount: Long, merchant: String, isCancel: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val initAmount = abs(item.amount ?: 0L)
    var amountText by remember { mutableStateOf(initAmount.toString()) }
    var merchant by remember { mutableStateOf(item.merchant ?: "") }
    var isCancel by remember { mutableStateOf((item.amount ?: 0L) < 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("항목 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("금액") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("가맹점/메모") },
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: return@TextButton
                    if (amount > 0L) onSave(amount, merchant.trim(), isCancel)
                },
                enabled = !processing
            ) {
                Text(if (processing) "저장 중..." else "저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !processing) { Text("취소") }
        }
    )
}

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "표시할 항목이 없습니다",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "card_filters.json에 룰이 없거나, 매칭된 알림이 이번 달에 없습니다.\n\n" +
                    "알람 로그에서 카드사 알림의 ts를 확인해 채팅으로 알려주시면 룰이 추가됩니다. " +
                    "그 후 메뉴 '업데이트'를 누르면 새 룰로 전체가 다시 분류됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun groupByCompany(
    context: android.content.Context,
    entities: List<NotificationEntity>
): List<CardSummaryGroup> {
    val filters = CardFilterStore.load(context).filters
    val filterIdToCompany = filters.associate { it.id to it.cardCompany }
    val pkgToCompany = filters.associate { it.packageName to it.cardCompany }
    return entities
        .groupBy {
            it.filterId?.let { fid -> filterIdToCompany[fid] }
                ?: pkgToCompany[it.pkg]
                ?: it.pkg
        }
        .map { (company, items) ->
            CardSummaryGroup(
                cardCompany = company,
                totalAmount = items.sumOf { it.amount ?: 0L },
                items = items.sortedByDescending { it.ts }
            )
        }
        .sortedByDescending { it.totalAmount }
}

private val TS_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

private fun formatTs(ts: Long): String = TS_FORMAT.format(Date(ts))
