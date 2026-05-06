# MyCard 소스 분석

작성일: 2026-04-27
대상: `D:\workspace\mycard\MyCard\` 이하 전체

---

## 1. 한 줄 요약

기기의 SMS 수신함을 읽어 이번 달 `[Web발신]` 카드 승인 문자를 사용자 정의 규칙(`전화번호,ID[,키워드]`)으로 그룹핑·합산하고, **Compose UI**와 **홈 화면 위젯** 두 곳에 노출하는 1인용 카드 사용액 트래커.

## 2. 빌드 / 의존성

| 항목 | 값 |
|---|---|
| `applicationId` | `com.example.mycard` |
| `namespace` | `com.example.mycard` |
| `compileSdk` / `targetSdk` | 36 / 36 |
| `minSdk` | 34 |
| Kotlin | 2.0.21 (+ Compose Compiler 플러그인) |
| AGP | 8.11.2 |
| JVM target | 11 |
| Compose BOM | 2024.09.00 |
| 그 외 | `androidx.work:2.10.0`, `androidx.documentfile:1.1.0`, `gson:2.11.0`(catalog 외부) |

`gradle/libs.versions.toml` 한 곳에서 버전을 관리. `local.properties`는 git-ignore.

## 3. 디렉터리 구조 (실 사용 파일만)

```
MyCard/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/mycard/
│       │   │   ├── MainActivity.kt
│       │   │   ├── SettingsActivity.kt
│       │   │   ├── CardRefreshWorker.kt
│       │   │   ├── sms/
│       │   │   │   └── SMSReader.java
│       │   │   ├── ui/theme/
│       │   │   │   ├── Color.kt
│       │   │   │   ├── Theme.kt
│       │   │   │   └── Type.kt
│       │   │   └── widget/
│       │   │       └── CardWidgetProvider.kt
│       │   └── res/
│       │       ├── layout/{widget_card.xml, widget_item.xml}
│       │       ├── xml/{card_widget_info.xml, backup_rules.xml, data_extraction_rules.xml}
│       │       ├── drawable/{damgom.jpg, ic_launcher_*.xml}
│       │       ├── values/{strings.xml, colors.xml, themes.xml}
│       │       └── mipmap-*/...
│       ├── test/.../ExampleUnitTest.kt           ← 2+2 짜리 샘플
│       └── androidTest/.../ExampleInstrumentedTest.kt
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

`widget_item.xml`은 정의돼 있지만 **현재 어디서도 inflate되지 않음** (잠재 dead code). 위젯은 `widget_card.xml` 단일 레이아웃에 그룹 5개를 줄바꿈 텍스트로 합쳐 그린다.

## 4. AndroidManifest

- 권한: `READ_SMS` 단일.
- 컴포넌트
  - `MainActivity` — LAUNCHER, exported.
  - `SettingsActivity` — 비공개, `label="설정"`.
  - `widget.CardWidgetProvider` — `AppWidgetProvider`. `APPWIDGET_UPDATE` + 커스텀 액션 `com.example.mycard.WIDGET_REFRESH` 인텐트 필터, exported.
- 백업 룰(`backup_rules.xml`, `data_extraction_rules.xml`)은 템플릿 그대로(주석만).
- 테마: `Theme.MyCard` = `android:Theme.Material.Light.NoActionBar`. Compose Activity는 자체 `MyCardTheme`을 덧씌움.

## 5. 컴포넌트별 분석

### 5.1 `SMSReader.java` — 파싱 코어

위치: `com.example.mycard.sms.SMSReader` (2026-04-27 `ui.theme` 패키지에서 이관).

핵심 API:
```java
public static List<SmsGroup> readCardApprovalGrouped(Context context)
```
처리 흐름:
1. `mycard_prefs`에서 `cardGroup` 문자열을 읽고 줄 단위로 분해. 각 줄은 `phone,id[,keyword]`(쉼표 구분).
2. 이번 달 1일 00:00:00의 timestamp를 `Calendar`로 계산.
3. 각 라인마다 별도 쿼리:
   ```
   uri      : content://sms/inbox
   columns  : address, date, body
   selection: address = ? AND date >= ? AND body LIKE '[Web발신]%'
   args     : [phone, startTimeMillis]
   order    : date DESC
   ```
4. 본문 정규화:
   - `^\[Web발신\]\s*` 제거 + 모든 공백 제거.
   - `configId.trim()`이 본문에 없으면 skip(즉 **id 자체가 본문에 포함돼야 매칭**).
   - `자동결제` / `자동 결제` → `승인`으로 치환.
5. `승인`/`취소`/금액(`\d[\d,]*원`) 중 아무것도 없으면 skip.
6. 금액 추출 패턴 선택:
   - `취소` 포함 → `CANCEL_AMOUNT_PATTERN` = `취소[^\d]*(\d[\d,]*)원`
   - 그 외 → `AMOUNT_PATTERN` = `승인[^\d]*(\d[\d,]*)원`
   - 첫 번째 매치만 사용. 매치 실패 시 skip.
7. `취소`면 금액을 **음수**로 저장 → 이후 그룹 합계에서 자연스럽게 차감됨.
8. `LinkedHashMap<id, List<SmsItem>>`에 누적 후 `List<SmsGroup>`로 반환.

데이터 모델:
```java
SmsItem  { String address, String date(yyyy-MM-dd HH:mm), String body, long amount }
SmsGroup { String id, long totalAmount, List<SmsItem> items }
```

미사용/하위호환 메서드:
- `findCardIdByPhone(phone, body, cardGroups)` — 본문 키워드까지 보고 ID를 결정하는 더 정교한 매칭. **현재 호출처 없음.** 라인 단위 쿼리(현재 동작)에서는 매칭이 자명하므로 자연 도태된 듯.
- `findCardGroup(body, cardGroups, defaultId)` — 더 단순한 본문 substring 매칭. 역시 호출처 없음.

문제점:
- **id 자체가 본문에 등장해야 한다는 추가 조건**(`if(!trimmedBody.contains(configId.trim())) continue;`)이 selection 조건과 별도로 in-loop 필터로 적용됨. id가 카드 별명·뒷자리 등 본문에 흔히 나오는 토큰이라는 전제. 그렇지 않으면 매치 0건.
- 같은 phone + 다른 id 두 줄이 설정돼 있으면 SMS 한 통이 두 그룹 모두에서 매치되어 **이중 집계** 가능 (동일 SMS가 두 row로 카운트됨).
- date 컬럼 정렬은 `DESC`인데 실제 표시 순서는 그룹 내 들어온 순(LinkedHashMap 삽입 순). 즉 그룹별로는 최신→오래된 순.

### 5.2 `MainActivity.kt` — Compose UI

`ComponentActivity` + `setContent { MyCardTheme { CardApprovalScreen(shouldRefresh) } }`.

`shouldRefresh`는 `intent.getBooleanExtra("refresh", false)`로 받음 — 위젯에서 액티비티를 띄울 때 새로고침을 강제하기 위한 트리거(현재 위젯 코드는 이 인텐트 경로를 사용하지 않음. PendingIntent는 `WIDGET_REFRESH` 브로드캐스트로 가도록 돼있음. → dead code 가능성).

`CardApprovalScreen` 내부 상태:
- `groups: List<SmsGroup>`
- `permissionGranted: Boolean`
- `showMenu: Boolean` (오른쪽 ⋮)
- `expandedGroups: Set<String>` (그룹별 펼침)

부수효과 4종:
1. `LaunchedEffect(shouldRefresh)` — 위젯 트리거 시 재로딩 + 위젯 prefs 갱신.
2. `LaunchedEffect(Unit)` — 첫 진입 시 권한 확인, 있으면 `groups` 채움, 없으면 권한 요청.
3. `LaunchedEffect(groups)` — `groups` 변할 때마다 `widget_total`/`widget_groups`를 prefs에 쓰고 `notifyAppWidgetViewDataChanged` 호출.
4. 상단바 새로고침 버튼 onClick — 같은 일을 또 함.
5. 추가로 `refreshData()` 함수가 정의돼 있지만 **호출되는 곳 없음**.

→ **위젯 prefs 갱신 + notify** 블록이 4번 거의 그대로 복붙(① 위젯 트리거, ② `LaunchedEffect(groups)`, ③ 상단바 onClick, ④ `refreshData()`). 5번째 추가 전에 헬퍼로 추출 권장.

UI 구조:
- `Scaffold` + `TopAppBar`("이번 달 카드 승인 내역") with `[새로고침 ↻] [⋮ → 설정]` actions.
- 권한 없음 상태: 안내 텍스트.
- 데이터 없음 상태: "이번 달 [Web발신] 카드 승인 문자가 없습니다." 텍스트.
- 데이터 있음:
  - 첫 카드: 이번 달 총 승인(전체 그룹 합계, primary 컨테이너).
  - `LazyColumn` items로 그룹 카드. 헤더 클릭 시 `expandedGroups`에 토글.
  - 펼치면 그룹 내 `SmsItem` 목록(날짜 / 본문 일부 / "승인" or "취소" / 금액). 본문은 `[Web발신]`을 제거하고 `누적.*` regex로 누적금 표기를 잘라냄.
  - 취소 항목은 금액을 양수로 표시하고 라벨만 "취소", 색상은 error.

### 5.3 `SettingsActivity.kt` — 설정 폼

저장소: `SharedPreferences("mycard_prefs", MODE_PRIVATE)`. 두 키 `cardGroup`, `memo`만 다룸.

UI: 두 개의 `OutlinedTextField`(카드그룹 / 메모) + 저장/취소 버튼.

저장은 `prefs.edit().putString(...).apply()`. 저장 후 `finish()`. 취소는 그냥 `finish()`.

placeholder는 "예: 스타벅스 / 테스트가맹점D / 네이버"로 돼있어 **포맷이 현실 입력(`전화번호,ID[,키워드]`)과 다름** — UX 결함.

### 5.4 `widget/CardWidgetProvider.kt` — 위젯

레이아웃: `widget_card.xml` (180dp×110dp, `updatePeriodMillis=1800000` = 30분).

`updateAppWidget(context, mgr, id)` 동작:
1. `mycard_prefs.widget_total: Long` → `R.id.widget_total`에 `"%,d원"`으로 셋.
2. `mycard_prefs.widget_groups: String`(JSON) → `parseGroupsJson()`로 `List<Pair<id, total>>` 추출.
3. 상위 5개를 `"id: %,d원\n"` 줄로 합쳐 `R.id.widget_groups_text`에 셋. 6개 이상이면 "... 외 N개" 추가.
4. `widget_refresh_btn`과 `widget_container`(전체 영역) 양쪽에 동일 PendingIntent(`WIDGET_REFRESH` 브로드캐스트, `FLAG_MUTABLE`)를 묶음.
5. 주석 처리된 `MainActivity` 실행 PendingIntent 코드가 남아있음 — 클릭으로 앱 진입은 현재 차단.

`onReceive` 분기:
- `WIDGET_REFRESH` 액션 수신 시 직접 `SMSReader.readCardApprovalGrouped(context)` 호출 → prefs 갱신 → `notifyAppWidgetViewDataChanged` + `updateAppWidget` 재호출.
- 기타 액션은 `super.onReceive`.

`parseGroupsJson(json)` — 정규식 기반 hand-rolled parser. `[{"id":"X","total":1234},...]`를 `},{`로 split → 따옴표/중괄호 제거 → `id:([^,]+)`, `total:(\\d+)` 두 정규식으로 추출. **id에 콤마/특수문자 들어가면 깨짐.** Gson을 이미 의존하고 있으니 교체 여지 큼.

### 5.5 `CardRefreshWorker.kt` — 미연결 워커

`CoroutineWorker`. `doWork()`가 하는 일:
1. `SMSReader.readCardApprovalGrouped(context)`.
2. `mycard_prefs`에 `widget_total` / `widget_groups` 저장 + 위젯 notify.
3. `card_approval_yyyyMMdd_HHmmss.txt`를 두 곳에 저장:
   - `getExternalFilesDir(null)` (앱 전용 외부저장소)
   - `getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)` (앱 전용 외부저장소 하위 Documents)
4. 성공 → `Result.success()`, 예외 → `Result.retry()`.

**현재 어디에서도 enqueue되지 않음.** `MainActivity.kt`가 `WorkManager`, `PeriodicWorkRequestBuilder`, `ExistingPeriodicWorkPolicy`, `TimeUnit`을 import만 해놨고 사용처 없음 → 사용하지 않는 import 다수.

## 6. 데이터 계약: `SharedPreferences("mycard_prefs", MODE_PRIVATE)`

| Key | Type | Writer | Reader |
|---|---|---|---|
| `cardGroup` | String (multi-line CSV) | `SettingsActivity` | `SMSReader` |
| `memo` | String | `SettingsActivity` | (현재 읽기만 — 다른 곳에서 사용 X) |
| `widget_total` | Long | `MainActivity` × 4, `CardWidgetProvider.onReceive`, `CardRefreshWorker` | `CardWidgetProvider.updateAppWidget` |
| `widget_groups` | String (자체 JSON) | 위와 동일 | `CardWidgetProvider.updateAppWidget` |

이 prefs가 Activity ↔ Worker ↔ Widget 간 단일 진실 공급원. 새 새로고침 경로를 추가할 때는 `widget_total` + `widget_groups`를 모두 갱신하고 `appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_total)`을 호출해야 일관성 유지.

## 7. 알려진 버그 / 코드 스멜

### 명백한 버그
- **`MainActivity.kt:208`**: 상단바 새로고침 버튼의 prefs 핸들이 `getSharedPreferences("l\`", Context.MODE_PRIVATE)` (문자열 `l` + 백틱). 다른 모든 경로는 `"mycard_prefs"`를 씀. 즉 **상단바 ↻ 버튼을 누르면 위젯 prefs가 엉뚱한 파일에 기록됨** → 그 직후 `notifyAppWidgetViewDataChanged`가 호출돼도 위젯이 읽는 prefs는 갱신되지 않음.
- **`refreshData()` (MainActivity.kt:165)** — 정의됐으나 호출되지 않는 dead 함수. 호출처는 모두 인라인으로 같은 로직 반복.
- **위젯 클릭 → 앱 실행이 비활성화** — 주석으로만 남아있고 클릭 액션은 새로고침 브로드캐스트로 일원화. UX상 의도된 결과인지 확인 필요.
- **`shouldRefresh` 인텐트 경로 동작 불가** — `MainActivity`는 `intent.getBooleanExtra("refresh", false)`를 기대하지만 위젯/워커 모두 이 인텐트를 발행하지 않음. 외부 진입 트리거가 살아있지 않음.

### 코드 스멜
- 위젯 prefs 갱신 + notify 블록이 4회 복붙(추출 필요).
- `parseGroupsJson` regex 파서 — Gson 있는 마당에 굳이.
- `MainActivity.kt`에 `WorkManager` 관련 import만 있고 사용처 0.
- `widget_item.xml`이 어디서도 사용되지 않음(잠재 dead resource).
- 한국어 주석에 `위젯` 오타 `위젷`이 반복(파일·라인 다수).

### 동작 함정
- 같은 `phone`을 두 줄에 다른 `id`로 등록하면 동일 SMS가 두 그룹에 중복 합산.
- `cardGroup` 한 줄이 `phone,id` 두 토큰 미만이면 그 줄은 조용히 무시.
- `SMSReader`의 본문 매칭은 `id` 문자열이 본문에 등장한다는 가정. 카드사 SMS 포맷에 의존.
- 위젯과 워커는 `READ_SMS` 권한이 이미 부여돼 있다고 가정. 미부여 상태에서 호출되면 **빈 결과를 조용히 prefs에 기록**(이전 데이터를 0원으로 덮어씀).

## 8. 데이터 흐름 다이어그램

```
            ┌────────────────────┐
            │  SettingsActivity  │  ──putString("cardGroup")──┐
            └────────────────────┘                            │
                                                              ▼
                                                  ┌──────────────────┐
                                                  │   mycard_prefs   │
                                                  │  cardGroup       │
                                                  │  widget_total    │
                                                  │  widget_groups   │
                                                  └──────────────────┘
                                                    ▲ ▲ ▲          │
                                                    │ │ │          │
   ┌──────────────┐    READ_SMS 쿼리   ┌──────────┐ │ │ │   read   ▼
   │ content://   │ ◀──────────────── │SMSReader │─┘ │ │  ┌─────────────────┐
   │ sms/inbox    │                    └──────────┘   │ │  │ CardWidget      │
   └──────────────┘                       ▲           │ │  │ Provider        │
                                          │ 호출      │ │  │ updateAppWidget │
                                          │           │ │  └─────────────────┘
                              ┌────────────────────┐  │ │           ▲
                              │   MainActivity     │──┘ │   notify  │
                              │ CardApprovalScreen │────┘──────────────┐
                              └────────────────────┘                   │
                                          ▲                            │
                              사용자 click │            BROADCAST       │
                              상단바 ↻    │      WIDGET_REFRESH         │
                                          │                            │
                              ┌────────────────────┐  ←─── 클릭 ───┐  │
                              │ CardRefreshWorker  │                │  │
                              │ (현재 미사용)       │              [위젯 영역]
                              └────────────────────┘
```

## 9. 정리: 기능 단위 평가

| 기능 | 상태 |
|---|---|
| SMS 파싱 / 그룹 합산 | 동작. 단 `id`가 본문에 포함된다는 전제. |
| 액티비티 UI 새로고침 | 동작(초기 로드, ⋮→설정, ↻ 표시). 단 ↻에 prefs 키 오타 버그. |
| 위젯 표시 | 동작. 30분 자동 업데이트 + 새로고침 버튼. |
| 위젯 → 앱 진입 | **막혀있음**(주석 처리). |
| 위젯 → 액티비티에 새로고침 신호 | **연결 안 됨**(`shouldRefresh` 사용처 없음). |
| 텍스트 파일 백업(`CardRefreshWorker`) | **enqueue 안 됨** → 미실행. |
| `memo` 설정값 | 저장만 되고 사용처 없음. |

## 10. 개선 포인트 (우선순위순)

1. **`MainActivity.kt:208`의 `"l\`"` 오타 수정 → `"mycard_prefs"`.** 단 사용자가 명시적으로 요청할 때만(현재 `CLAUDE.md` 정책).
2. 위젯 prefs 갱신 + notify 블록을 헬퍼로 추출(`refreshData()`를 실제로 사용).
3. `parseGroupsJson`을 Gson으로 교체 — id에 콤마/따옴표 들어가는 케이스 방어.
4. `CardWidgetProvider.onReceive`와 `MainActivity`의 권한 미부여 케이스에 빈 결과 기록을 막는 가드(권한 없으면 prefs를 건드리지 않음).
5. `SettingsActivity` placeholder를 실제 포맷(`010-1234-5678,스벅`)으로 교정.
6. 미사용 코드 제거 또는 활성화: `refreshData`, `findCardIdByPhone`/`findCardGroup`, `MainActivity`의 WorkManager import, `widget_item.xml`, `MainActivity`의 `shouldRefresh` 인텐트 경로.
7. ~~`SMSReader.java`를 `com.example.mycard.sms` 같은 적절한 패키지로 이동~~ — **2026-04-27 완료** (`com.example.mycard.sms.SMSReader`).

---

## 부록 A. 정규식 모음

| 이름 | 패턴 | 용도 |
|---|---|---|
| `AMOUNT_PATTERN` | `승인[^\d]*(\d[\d,]*)원` | 승인/자동결제 금액 추출 |
| `CANCEL_AMOUNT_PATTERN` | `취소[^\d]*(\d[\d,]*)원` | 취소 금액 추출 |
| `DEFAULT_AMOUNT_PATTERN` | `(\d[\d,]*)원` | 정의는 됐으나 코드 경로상 사용 안 됨 |
| (인라인) | `\d[\d,]*원` | "금액 표기 존재 여부" 사전 체크 |
| (UI 인라인) | `누적.*` | 본문 표시에서 누적금 부분 잘라내기 |

## 부록 B. SQL selection (SMS Provider)

```
SELECT address, date, body
FROM   content://sms/inbox
WHERE  address = ?            -- cardGroup 라인의 phone
  AND  date    >= ?           -- 이번 달 1일 00:00:00 millis
  AND  body LIKE '[Web발신]%'
ORDER BY date DESC
```
