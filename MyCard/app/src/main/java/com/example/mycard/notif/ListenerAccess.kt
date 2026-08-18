package com.example.mycard.notif

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

/** 알림 접근 권한 상태. 이 앱의 모든 카드 집계가 이 권한 하나에 달려 있다. */
fun isListenerPermissionGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

fun openListenerSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

/**
 * 권한이 꺼졌을 때만 띄우는 경고 배너.
 *
 * 이 앱은 알림을 읽어서 카드 내역을 만들기 때문에, 권한이 꺼지면 에러 없이 그냥
 * 데이터가 멈춘다 — 화면에는 예전 합계가 그대로 남아 "금액이 안 맞는" 것처럼 보인다.
 * 그 상태를 사용자가 즉시 알 수 있게 카드 목록 위에 노출한다.
 */
@Composable
fun ListenerAccessWarningBanner(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFFEBEE))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("알림 접근 권한이 꺼져 있습니다", fontWeight = FontWeight.Bold)
            Text(
                "새 결제가 집계되지 않습니다. 설정 → 알림 → 알림 접근 → MyCard 허용",
                fontSize = 12.sp,
                color = Color.DarkGray
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onOpenSettings) { Text("설정 열기") }
    }
}

/** 한도 초과 요약 배너. 초과한 카드가 하나 이상일 때만 노출한다. */
@Composable
fun OverLimitBanner(overLimitCount: Int, totalExcess: Long) {
    if (overLimitCount <= 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFFF3E0))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("⚠", fontSize = 18.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text("월 한도 초과 ${overLimitCount}개 카드", fontWeight = FontWeight.Bold)
            Text(
                "초과 합계 %,d원".format(totalExcess),
                fontSize = 12.sp,
                color = Color.DarkGray
            )
        }
    }
}
