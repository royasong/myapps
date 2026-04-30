package com.example.mycard.notif

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycard.notif.db.NotificationDatabase
import com.example.mycard.notif.db.NotificationEntity
import com.example.mycard.parser.CardFilterStore
import com.example.mycard.ui.theme.MyCardTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBasedCardScreen() {
    val context = LocalContext.current
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
                                val displayAmount = if (isCancel) -amount else amount
                                val typeText = if (isCancel) "취소" else "승인"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
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
    val pkgToCompany = CardFilterStore.load(context).filters
        .associate { it.packageName to it.cardCompany }
    return entities
        .groupBy { pkgToCompany[it.pkg] ?: it.pkg }
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
