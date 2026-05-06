# MainActivity 데이터 소스 전환: SMS → 알림 DB (2026-05-03)

## 목적

SMS 기반(`SMSReader.readCardApprovalGrouped`) 데이터 소스를 알림 DB 기반으로 교체.
MainActivity UI·위젯 업데이트 코드는 그대로 유지, 데이터 로딩 함수 호출 부분만 교체.

## 변경 파일

### 신규: `notif/NotifCardReader.kt`

`suspend fun readNotifCardGroups(context: Context): List<SMSReader.SmsGroup>`

- 이번 달 시작 ts 계산 → `dao.getParsedSince(startOfMonth)` 조회
- `filterId → cardCompany` 매핑으로 그룹화
- `NotificationEntity` → `SMSReader.SmsGroup` / `SmsItem` 변환
  - `SmsItem.date` ← `formatTs(entity.ts)` (yyyy-MM-dd HH:mm)
  - `SmsItem.body` ← `entity.merchant ?: entity.text ?: entity.title`
  - `SmsItem.amount` ← `entity.amount ?: 0L`

### `notif/db/NotificationDao.kt`

`suspend fun getParsedSince(sinceTs: Long): List<NotificationEntity>` 추가.

### `MainActivity.kt`

`SMSReader.readCardApprovalGrouped(context)` → `readNotifCardGroups(context)` 5곳 교체:

| 위치 | 변경 방식 |
|---|---|
| `LaunchedEffect(shouldRefresh)` | 직접 교체 (이미 suspend context) |
| `LaunchedEffect(Unit)` | 직접 교체 |
| `BroadcastReceiver.onReceive` | `coroutineScope.launch { }` 래핑 |
| `permissionLauncher` callback | `coroutineScope.launch { }` 래핑 |
| `refreshData()` | `coroutineScope.launch { }` 래핑, 중복 위젯 업데이트 코드 제거 (LaunchedEffect(groups)가 담당) |

## 주의

- `SMSReader` import는 그대로 유지 — `List<SMSReader.SmsGroup>` 타입 참조에 사용
- `refreshData()` 안의 위젯 업데이트 중복 코드 제거 → `LaunchedEffect(groups)`가 동일 동작 수행
- main branch cherry-pick 시 `NotifCardReader.kt`, `NotificationDao.kt` 변경은 충돌 없음. `MainActivity.kt`는 함수 이름·launch 래핑 차이만 존재
