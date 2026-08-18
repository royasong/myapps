package com.example.mycard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 카드별 월 한도 입력 다이얼로그.
 * [companies]는 이번 달에 실제로 집계된 그룹 + 이미 한도가 설정된 그룹의 합집합.
 */
@Composable
fun CardLimitDialog(
    companies: List<String>,
    currentLimits: Map<String, Long>,
    onSave: (Map<String, Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var fields by remember(companies) {
        mutableStateOf(companies.associateWith { currentLimits[it]?.toString().orEmpty() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("카드별 월 한도", fontWeight = FontWeight.Bold) },
        text = {
            if (companies.isEmpty()) {
                Text("아직 집계된 카드가 없습니다. 내역이 쌓이면 여기서 카드별 월 한도를 정할 수 있습니다.")
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "빈 칸이나 0으로 두면 한도를 해제합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    companies.forEach { company ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CardAvatar(company)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    company,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                                OutlinedTextField(
                                    value = fields[company].orEmpty(),
                                    onValueChange = { v ->
                                        fields = fields + (company to v.filter { it.isDigit() })
                                    },
                                    placeholder = { Text("한도 없음") },
                                    suffix = { Text("원") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(fields.mapValues { (_, v) -> v.toLongOrNull() ?: 0L })
                onDismiss()
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

/**
 * 그룹 헤더 2행 오른쪽에 붙는 한도 상태 한 줄.
 * 초과면 초과분, 여유가 있으면 잔여를 보여준다. 큰 글꼴에서도 줄바꿈되지 않게 짧게 유지한다.
 */
@Composable
fun CardLimitStatus(
    monthlyLimit: Long,
    total: Long,
    modifier: Modifier = Modifier
) {
    val over = total > monthlyLimit
    Text(
        text = if (over) {
            "⚠ +%,d원".format(total - monthlyLimit)
        } else {
            "잔여 %,d원".format(monthlyLimit - total)
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (over) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        maxLines = 1,
        modifier = modifier
    )
}
