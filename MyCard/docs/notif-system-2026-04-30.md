# 알림 기반 시스템 — 동작 구조 (2026-04-30)

이 문서는 MyCard의 "알림 기반" 파이프라인 — `CardNotificationListener`부터 `NotificationDatabase` 및 외부 저장 파일까지 — 의 실제 데이터 흐름을 정리한다. SMS 기반 흐름(`SMSReader`)은 별도이며 이 문서 범위 밖.

## 1. 컴포넌트 한눈에 보기

| 컴포넌트 | 위치 | 역할 |
|---|---|---|
| `CardNotificationListener` | `notif/` | OS 알림 수신 진입점 (NotificationListenerService) |
| `RawDumpAll` | `notif/` | **모든** 알림을 그대로 외부 jsonl에 dump (디버깅/포렌식) |
| `Blacklist` | `notif/` | 패키지 제외 목록 (DB·RawDump에서 빠짐) |
| `Whitelist` | `notif/` | 파싱 대상 패키지 목록 (CardParser 발동 조건) |
| `CardFilterStore` | `parser/` | `card_filters.json` 로딩·캐시·invalidate |
| `CardParser` | `parser/` | (title, body)를 regex로 매칭해 `amount/merchant/type` 추출 |
| `NotificationDatabase` (Room) | `notif/db/` | 앱 내부의 1차 저장소. 알림 로그·알림 기반 보기의 source |
| `RawDump` | `notif/` | 비-블랙리스트 알림의 외부 jsonl 미러 (DB 재구성용 backup) |
| `UpdateAction.rebuildFromRaw` | `notif/` | RawDump → DB 재구성 (메뉴 "업데이트") |
| `NotificationListActivity` | `notif/` | UI: 알림 로그 (DB의 모든 row 시간 역순) + Whitelist 관리 |
| `NotificationBasedCardActivity` | `notif/` | UI: 알림 기반 보기 (DB에서 amount≠null 항목 그룹화) |

## 2. 외부 저장 파일 (5개)

모두 `/sdcard/Documents/MyCard/` 직접 경로. `MANAGE_EXTERNAL_STORAGE` 권한 필요 (uninstall 후 재토글).

| 파일 | 포맷 | 누가 쓰나 | 누가 읽나 |
|---|---|---|---|
| `whitelist.txt` | text/plain, 한 줄 = 패키지 | `Whitelist.add/remove` | `Whitelist.ensureLoaded` (없으면 hard-coded DEFAULT 10개로 초기화 후 저장) |
| `blacklist.txt` | text/plain, 한 줄 = 패키지 | `Blacklist.add/remove` (UI: 알림 로그 long-press) | `Blacklist.ensureLoaded` (없으면 빈 set) |
| `raw_notifications.jsonl` | pretty-printed JSON 객체 sequence | `RawDump.appendObject` (listener의 비-블랙리스트 분기) | `UpdateAction.rebuildFromRaw` (DB 재구성), `NotificationListActivity` 삭제 시 동기화 |
| `raw_notifications_all.jsonl` | 동일 포맷, **모든** 알림 (블랙리스트 포함) | `RawDumpAll.appendObject` (listener 무조건 호출) | (앱 코드에선 안 읽음 — 외부 디버깅 전용) |
| `card_filters.json` | `CardFiltersFile` JSON | `CardFilterStore.saveAll` (현재 코드 호출처 없음 — 사용자 adb push로 갱신) | `CardFilterStore.load` (캐시) |

> 모든 파일은 `java.io.File` 직접 R/W. (이전 MediaStore 기반 코드는 uninstall 후 owner mismatch로 EACCES, 2026-04-30에 제거됨.)

## 3. 데이터 흐름 — 알림 도착 시 (`onNotificationPosted`)

```
StatusBarNotification 도착
  ↓
baseEntity 빌드 (id=0, ts, pkg, title, text, bigText, subText, category, channelId, rawExtras)
Log.d "posted pkg=… title=…"
  ↓
[scope.launch IO]
  ↓
RawDumpAll.appendObject(baseEntity)  ─→  raw_notifications_all.jsonl  (모든 알림, 무조건)
  ↓
Blacklist.contains(pkg)?  YES ──→ return  (DB·RawDump 둘 다 skip)
  ↓ NO
Whitelist.contains(pkg)?  YES → CardParser.parse → ParseResult? (amount, merchant, type, filterId)
                          NO  → null
  ↓
finalEntity = parsed?일때 amount/merchant/parsedAt 채워서 copy, 아니면 baseEntity 그대로
  ↓
NotificationDatabase.dao.insert(finalEntity)  →  newId
  ↓
RawDump.appendObject(finalEntity.copy(id=newId))  ─→  raw_notifications.jsonl
```

핵심:
- **블랙리스트**는 강한 차단: DB도 RawDump도 안 들어감 (RawDumpAll에는 기록됨).
- **화이트리스트는 파싱 트리거이지 DB 진입 조건이 아님**: 화이트리스트 밖이어도 비-블랙리스트면 `amount=null`로 DB에 들어간다.
- 즉 DB에는 "비-블랙리스트 모든 알림"이 들어가고, 그 중 일부만 amount가 채워진다.

## 4. 데이터 흐름 — 메뉴 "업데이트" (`UpdateAction.rebuildFromRaw`)

```
CardFilterStore.invalidate()   ┐
Whitelist.invalidate()         │ 디스크에서 최신 상태로 모두 재로드
Blacklist.invalidate()         │ (사용자가 adb로 .txt/.json/.jsonl을 갱신했어도 즉시 반영)
RawDump.invalidate()           ┘
  ↓
RawDump.readAllObjects()  →  List<JsonObject>
  ↓
empty? YES → RebuildResult(0,0,0,0) 반환
  ↓ NO
NotificationDatabase.dao.clear()  (DB 비움)
  ↓
for each obj:
   parseObject(obj) → entity (id/ts/pkg/title/text/...)
   Blacklist.contains(pkg)?  YES → skippedByBlacklist++, continue
   Whitelist.contains(pkg)?  YES → CardParser.parse(entity) → parseResult?
                             NO  → parseResult=null
   parseResult?  null & whitelist에 있었으면 skippedByParseFail++
   finalEntity = parsed?때 amount/merchant/parsedAt 채움, 아니면 entity 그대로
   dao.insert(finalEntity)  →  rebuilt++ (parsed 채워졌으면 parsed++ 도)
  ↓
RebuildResult(rebuilt, parsed, skippedByBlacklist, skippedByParseFail)
  ↓
Snackbar: "재구성 N / 파싱 N / blacklist N / 파싱실패 N"
```

핵심:
- DB를 **완전히 비우고** raw에서 재구성. listener와 별개의 ground-truth 재시드 경로.
- **4개 cache 모두 invalidate 후 시작** — adb로 외부 파일을 수정한 직후 메뉴 "업데이트" 한 번이면 whitelist/blacklist/card_filters/raw_notifications 모두 새로 적용된다. 앱 재시작 불필요.
- "재구성"은 DB에 들어간 row 수 (블랙리스트 제외 전체).
- "파싱"은 amount가 채워진 row 수 (whitelist + filter 매칭).
- "blacklist"는 raw에 있었지만 블랙리스트라 DB 진입 거부된 수.
- "파싱실패"는 whitelist이지만 어느 filter regex와도 매치 안 된 수.

## 5. UI 화면

### MainActivity (홈, SMS 기반 view)
- 상단 refresh 아이콘 → `refreshData()` (SMS 기반 — `SMSReader.readCardApprovalGrouped`)
- ⋮ 오버플로우 메뉴 (정의 순서):
  1. **설정** → SettingsActivity
  2. **알림 로그** → NotificationListActivity
  3. **업데이트** → `UpdateAction.rebuildFromRaw` + Snackbar 결과 표시
  4. **알림 기반 보기** → NotificationBasedCardActivity
- `LaunchedEffect(Unit)` — READ_SMS·RECEIVE_SMS 런타임 권한 요청 + `MANAGE_EXTERNAL_STORAGE` 부재 시 Snackbar 안내(액션=설정 화면 열기).

### NotificationListActivity (알림 로그)
- `dao.observeAll()`로 DB의 모든 row 시간 역순 LazyColumn 표시 (블랙리스트 외 전부, amount 유무 무관).
- 권한 상태 배너:
  - **알림 접근 권한** — 항상 표시 (granted 여부에 따라 안내문/색상 변경)
  - **배터리 최적화 제외** — 미설정 시에만 표시. 설정되면 자동 숨김 (화면 공간 절약).
- 카드 long-press → DropdownMenu (3개 항목, 정의 순서):
  1. **화이트리스트 추가/제거** — `Whitelist.add` 또는 `remove` (toggle)
  2. **블랙리스트 추가** — `Blacklist.add` + `Whitelist.remove`(있다면) + `dao.deleteByPkg(pkg)` + `RawDump.removeLinesByPkg(pkg)`
  3. **삭제** — `dao.deleteById(id)` + `RawDump.removeLineById(id)` (해당 카드 한 건만)
- swipe-to-delete는 **사용 안 함** (실수 삭제 방지). 모든 삭제는 long-press 메뉴에서.
- 삭제 시 모두 `RawDump`(`raw_notifications.jsonl`)에서도 함께 제거. `RawDumpAll`(`raw_notifications_all.jsonl`)은 **건드리지 않음** — forensic/디버깅용 원본 보존.

### NotificationBasedCardActivity (알림 기반 보기)
- DB에서 `amount IS NOT NULL`인 row만 → `CardFilterStore`의 `card_company`로 그룹화.
- 그룹별 `formatTs(ts) | merchant/title | type/금액` 2열 레이아웃 (SMS 보기와 통일).

## 6. 파일/DB 수명 — uninstall 시

| 데이터 | 위치 | uninstall 후 |
|---|---|---|
| `mycard_prefs` (cardGroup 등) | `/data/data/com.example.mycard/shared_prefs/` | **소실** (Android auto-backup이 복원하기도 함, 의존 금지) |
| Room DB (`notifications` 테이블) | `/data/data/com.example.mycard/databases/` | **소실** → 메뉴 "업데이트"로 raw에서 재구성 |
| 5개 외부 파일 | `/sdcard/Documents/MyCard/*` | **유지** (다만 MediaStore row owner는 mtp/null로 reassign됨) |
| 알림 접근·MANAGE_EXTERNAL_STORAGE | OS-level | **revoke** → 재설치 후 직접 토글 필요 |

복구 절차 (재설치 후):
1. 알림 접근 권한 ON
2. 모든 파일 액세스 권한 ON (앱이 Snackbar로 안내)
3. 메뉴 "업데이트" → DB 재구성

## 7. CardFilter 스키마 (`card_filters.json`)

```kotlin
data class CardFiltersFile(version: Int, updated_at: String, filters: List<CardFilter>)
data class CardFilter(id, card_company, package, match: Match, examples, added_at, added_from_ts)
data class Match(title_regex: String?, body_regex: String?, type: "approval" | "cancel")
```

`CardParser.tryMatch`:
- `titleRegex`가 있으면 title.match(); 매칭 안 되면 null.
- `bodyRegex`가 있으면 (bigText 우선, 없으면 text).match(); 매칭 안 되면 null.
- 명명 그룹 `<amount>` 추출 → `replace(",", "").toLong()`.
- 명명 그룹 `<merchant>` 추출 (선택).
- `match.type == "cancel"`이면 amount 부호 반전 (음수 저장).

## 8. 삭제·이동 동작 정리

| 사용자 액션 | DB | RawDump (`raw_notifications.jsonl`) | RawDumpAll (`raw_notifications_all.jsonl`) | Whitelist | Blacklist |
|---|---|---|---|---|---|
| 카드 long-press → 화이트리스트 추가/제거 | — | — | — | toggle | — |
| 카드 long-press → 블랙리스트 추가 | 해당 pkg 전부 삭제 | 해당 pkg 전부 삭제 | — | 있었으면 제거 | 추가 |
| 카드 long-press → 삭제 | 해당 row 삭제 | 해당 row 삭제 | — | — | — |
| 새 알림 도착 (listener) | 추가 (블랙리스트 외) | 추가 (블랙리스트 외) | 추가 (무조건) | — | — |
| 메뉴 "업데이트" | clear 후 raw 기준 재구성 | 읽기만 | — | invalidate→재로드 | invalidate→재로드 |

핵심 invariant: **`RawDumpAll`은 사용자가 직접 파일을 지우지 않는 한 절대 줄어들지 않는다.** 룰 작성·회귀 테스트의 ground truth.

## 9. 자주 헷갈리는 포인트

- **"업데이트는 whitelist만 DB에 쓰는가?"** — 아니다. **블랙리스트만 제외하고 모두** DB에 들어간다. Whitelist는 "추가로 파싱해서 amount/merchant를 채울지" 결정할 뿐. 따라서 알림 로그 화면은 비-블랙리스트 전체를 보여주고, 알림 기반 보기는 amount 있는 것만 보여준다.
- **`raw_notifications.jsonl` vs `raw_notifications_all.jsonl`** — 앞의 것은 "DB 진입 후보"의 mirror (블랙리스트 빠진 상태), 뒤의 것은 "내가 받은 모든 알림" (블랙리스트도 포함). 디버깅·룰 작성용 raw data는 `_all` 쪽을 쓴다 (`docs/data/<카드사>.jsonl` 아카이브 소스).
- **listener의 RawDump write가 DB insert 뒤에 일어남** — `id=newId`를 채워 외부 미러로 남기기 위함. listener와 업데이트 동시에 돌면 DB row id와 jsonl id가 중복될 수 있어, `UpdateAction`은 `clear()` 후 새 autogenerate id로 재insert (jsonl의 id는 기록 시점의 것일 뿐, rebuild 후엔 무의미).
- **`Whitelist`가 비어있으면 default 10개로 자동 부트스트랩 + 저장**. `Blacklist`는 빈 채로 두면 비어 있다.
- **카드사 앱 vs 페이 앱**: 페이 앱(`com.samsung.android.spay` 등)은 결제 직후 알림이 오지만 카드 식별이 안 돼 룰 대상이 부적합. 블랙리스트에 넣어 RawDump 진입 자체를 막는 게 보통.
- **adb로 `whitelist.txt`/`blacklist.txt`/`card_filters.json` 갱신 후 적용 방법**: 메뉴 "업데이트" 한 번 누르면 4개 cache(filter/white/black/raw) 모두 invalidate 후 디스크에서 새로 읽어 DB 재구성. 앱 재시작 불필요.
- **swipe-to-delete 없음** — 실수 삭제 방지를 위해 일부러 제거됨. 삭제는 long-press → "삭제" 3번째 메뉴.
- **배터리 최적화 banner는 미설정 시에만 표시** — 한 번 토글 후 설정되면 banner는 사라져 화면 공간을 차지하지 않는다.
