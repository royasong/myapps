package com.example.mycard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 그룹 이름(card_filters.json의 card_company)에서 카드사 브랜드와 짧은 라벨을 뽑아낸다.
 * MyCard의 그룹은 카드사가 아니라 "카드 한 장" 단위(예: "하나 매직 9207")이므로,
 * 라벨은 카드사 이름보다 뒷 4자리를 우선한다 — 같은 카드사 카드를 눈으로 구분하는 데 그게 유용하다.
 */
data class CardBrand(
    val label: String,
    val top: Color,
    val bottom: Color,
    val onBrand: Color = Color.White,
    val chip: Color = Color(0xFFE3C07B)
)

private val LAST4 = Regex("""\d{4}""")

fun cardBrandOf(company: String): CardBrand {
    val name = company.trim()
    val label = shortLabel(name)
    return when {
        name.contains("매출취소") || name.contains("취소") ->
            CardBrand(label, Color(0xFF9AA3AF), Color(0xFF5B6472), chip = Color(0xFFD8DCE2))

        name.contains("현백") || name.contains("현대백화점") ->
            CardBrand(label, Color(0xFF8C2440), Color(0xFF4E1224))

        name.contains("네이버") ->
            CardBrand(label, Color(0xFF06C167), Color(0xFF01894A))

        // SK 계열(SKT-M / SK브로드밴드)은 현대카드 발급이지만 브랜드 색은 SK 레드가 눈에 잘 붙는다.
        name.contains("SK") ->
            CardBrand(label, Color(0xFFF0323C), Color(0xFFAE0C1B))

        name.contains("현대") ->
            CardBrand(label, Color(0xFF3A3A3C), Color(0xFF101012))

        name.contains("신한") ->
            CardBrand(label, Color(0xFF2A6FF0), Color(0xFF10399E))

        name.contains("하나") ->
            CardBrand(label, Color(0xFF009C93), Color(0xFF005F5A))

        name.contains("롯데") ->
            CardBrand(label, Color(0xFFE33B41), Color(0xFF97121A))

        name.contains("삼성") ->
            CardBrand(label, Color(0xFF2B4BC8), Color(0xFF0F1C6B))

        name.contains("국민") || name.contains("KB") ->
            CardBrand(label, Color(0xFFFFC53D), Color(0xFFD08A00), onBrand = Color(0xFF3A2A00), chip = Color(0xFF8A6100))

        name.contains("우리") ->
            CardBrand(label, Color(0xFF1E86D6), Color(0xFF04487F))

        name.contains("농협") || name.contains("NH") ->
            CardBrand(label, Color(0xFF19B75F), Color(0xFF037538))

        name.contains("비씨") || name.contains("BC") ->
            CardBrand(label, Color(0xFFF57722), Color(0xFFAE4406))

        else ->
            CardBrand(label, Color(0xFF6366F1), Color(0xFF3730A3))
    }
}

private fun shortLabel(name: String): String {
    LAST4.find(name)?.let { return it.value }
    if (name.contains("매출취소") || name.contains("취소")) return "취소"
    if (name.contains("네이버")) return "네이버"
    if (name.contains("SK")) return "SK"
    if (name.contains("현백") || name.contains("현대백화점")) return "현백"
    val base = name.replace("카드", "").trim()
    val head = base.split(Regex("""\s+""")).firstOrNull().orEmpty().ifEmpty { name }
    return head.take(3)
}

/**
 * 실제 신용카드 비율(약 1.6:1)의 미니 카드 아바타.
 * 브랜드 그라데이션 + IC 칩 + 라벨로, 목록에서 카드를 색과 숫자로 동시에 구분할 수 있게 한다.
 */
@Composable
fun CardAvatar(
    company: String,
    modifier: Modifier = Modifier,
    width: Int = 46,
    height: Int = 30
) {
    val brand = cardBrandOf(company)
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .width(width.dp)
            .height(height.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(brand.top, brand.bottom),
                    start = Offset.Zero,
                    end = Offset(width * 2f, height * 3f)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.20f), shape)
    ) {
        // IC 칩
        Box(
            modifier = Modifier
                .padding(start = 5.dp, top = 5.dp)
                .size(width = 9.dp, height = 7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(brand.chip)
        )
        // 상단 광택
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((height / 2).dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.13f), Color.Transparent)
                    )
                )
        )
        Text(
            text = brand.label,
            color = brand.onBrand,
            fontSize = if (brand.label.length >= 4) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 5.dp, bottom = 3.dp)
        )
    }
}
