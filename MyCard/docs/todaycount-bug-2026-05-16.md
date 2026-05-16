# todayCount 불일치 버그 수정 (2026-05-16)

## 증상

헤더의 `($todayCount/$totalCount)` 오늘 횟수와 각 그룹 카드의 `오늘 N / 전체 N` 오늘 횟수 합계가 일치하지 않는 경우가 있었음.

## 원인

`MainActivity.kt`의 `todayStr` 변수가 `remember { }` (키 없음)로 감싸져 있어서 컴포저블 최초 진입 시 **딱 한 번만** 날짜를 계산했음.

```kotlin
// 수정 전 — 오늘 날짜가 캐시되어 갱신되지 않음
val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date()) }
val todayCount = groups.sumOf { group -> group.items.count { it.date.startsWith(todayStr) } }
```

- **헤더 `todayCount`**: `startsWith(todayStr)` 사용 → `remember{}` 캐시 날짜 기준
- **그룹별 `todayItemCount`**: `isToday(it.date)` 사용 → `LocalDate.now()` 매번 호출

자정을 넘어서 앱을 사용하는 경우, 헤더는 어제 날짜로 계산하고 그룹 카드는 오늘 날짜로 계산해 불일치가 발생함.

## 수정

`todayStr` 제거 후 헤더 `todayCount`도 `isToday()`로 통일.

```kotlin
// 수정 후 — 양쪽 모두 isToday() 사용으로 일관성 확보
val totalCount = groups.sumOf { it.items.size }
val todayCount = groups.sumOf { group -> group.items.count { isToday(it.date) } }
```

`isToday()` 함수는 `LocalDate.now()`를 매번 호출하므로 항상 현재 날짜 기준으로 계산됨.

## 수정 파일

- `app/src/main/java/com/example/mycard/MainActivity.kt` (line 294–296)
