# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Scope (중요)

**본 프로젝트의 개발 대상은 `MyCard/` 뿐이며, `arin/`은 개발 범위가 아니다.** 작업은 **`D:\workspace\mycard\MyCard\` 이하에서만** 수행합니다.

- 형제 디렉터리 `D:\workspace\mycard\arin\`는 **읽지도 말고, 검색·편집·참조도 하지 않습니다.**
- 상위 디렉터리의 `D:\workspace\mycard\CLAUDE.md`, `D:\workspace\CLAUDE.md`, 그리고 사용자 홈의 글로벌 `~/.claude/CLAUDE.md` **세 파일은 절대 편집하지 않는다**. 이 프로젝트에서 편집 가능한 CLAUDE.md는 **이 파일(`MyCard/CLAUDE.md`)뿐**. 두 프로젝트가 함께 언급된 부분이 있어도 이 프로젝트 작업에서는 MyCard 부분만 따릅니다.
- Glob/Grep/Read 등 모든 도구 호출은 이 디렉터리(또는 그 하위)로 한정합니다. 워크스페이스 전체를 훑는 광역 검색 금지.

## PR 기본 base 브랜치

**모든 PR의 base는 항상 `sach_dev`로 한다.** `main` 직접 PR 생성 금지 (사용자가 명시적으로 "main으로 가자"라고 하지 않는 이상).

- 흐름: `feature/xxx` → `sach_dev`(통합) → `main`(별도 PR로 합병)
- `/ship`, `/br-pr` 등 PR 생성 시 base 선택 옵션이 나오면 자동으로 sach_dev 선택.
- 이미 PR이 main을 base로 만들어졌으면 `gh pr edit <num> --base sach_dev`로 즉시 옮긴다.

## What this app does

Reads the device's SMS inbox, filters this-month `[Web발신]` card-approval messages by user-configured `phone,id[,keyword]` rules, groups them by id, sums approvals minus cancellations, and surfaces the totals in:
- a Compose Activity UI (`MainActivity`),
- a home-screen `AppWidget` (`CardWidgetProvider`).

Permission used: **`READ_SMS` only.** UI strings, comments, and log text are in Korean.

## Build / run

```bash
./gradlew assembleDebug                                   # debug APK
./gradlew installDebug                                    # install on connected device
./gradlew :app:testDebugUnitTest                          # JVM unit tests
./gradlew :app:testDebugUnitTest --tests "com.example.mycard.ExampleUnitTest.someMethod"
./gradlew :app:connectedAndroidTest                       # instrumented tests (needs device)
./gradlew lint
./gradlew clean
```

`local.properties` is git-ignored here (unlike `arin/`). If a build complains about the SDK location, create `local.properties` with `sdk.dir=...`.

Toolchain: Kotlin 2.0.21, AGP 8.11.2, Compose Compiler plugin, Compose BOM 2024.09.00, JVM target 11, `compileSdk 36`, `minSdk 34`, `targetSdk 36`. Versions are in `gradle/libs.versions.toml`. The only non-AndroidX runtime dep is Gson 2.11.0 (declared directly in `app/build.gradle.kts`, not via the catalog).

## Architecture

### Source of truth: `SharedPreferences("mycard_prefs", MODE_PRIVATE)`

This single prefs file is shared between Activity, Worker, and Widget. Keys:
- `cardGroup: String` — newline-separated config, each line `phone,id[,last4]`. `phone` is the SMS sender address (exact match), `id` is the display group label and is also required to appear as a substring in the message body. The optional `last4` is the user's full 4-digit card number tail (e.g., `0179`); it's matched **position-by-position** against the masked card token in the body (e.g., `0*7*` — `*` is a wildcard for any digit). This lets the user split multiple cards from the same sender into per-card filters. Written by `SettingsActivity`, consumed by `SMSReader`.
- `memo: String` — free-form note in `SettingsActivity`.
- `widget_total: Long` — last-computed grand total, rendered by the widget.
- `widget_groups: String` — hand-rolled JSON `[{"id":"X","total":1234},...]` listing per-group totals, rendered by the widget.

**If you add a new refresh path, it must update both `widget_total` and `widget_groups` AND call `appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_total)`.** There are already 4 near-duplicate copies of this block in `MainActivity.kt` (initial load, `LaunchedEffect(groups)`, top-bar refresh button, and `refreshData()`). Extract a helper before adding a 5th.

### Components

> **SMSReader.java 위치:** `app/src/main/java/com/example/mycard/sms/SMSReader.java` (`sms/` 패키지). 과거에는 `ui/theme/SMSReader.java`였으나 이전 작업에서 의도적으로 옮긴 것이다. **다시 `ui/theme/`로 되돌리지 말 것.** main 브랜치 등 다른 라인에 옛 경로(`ui/theme/SMSReader.java`)를 수정한 commit이 남아 있어 cherry-pick/merge 시 modify/delete 충돌이 날 수 있다. 그런 commit의 SMSReader 변경은 **현재 `sms/SMSReader.java`에 더 정교한 RCS 파싱(`addRcsApprovals` 등)이 이미 있어 대부분 무가치**하므로, 충돌 시 옛 경로 파일을 다시 만들지 말고 `git rm`으로 삭제 측을 선택하면 된다. 의미 있는 동작 변화가 있으면 그것만 발췌해 `sms/SMSReader.java`에 수동 적용.

- `app/src/main/java/com/example/mycard/sms/SMSReader.java` — parsing core. `readCardApprovalGrouped(context)` reads `cardGroup` from prefs and runs **two parallel queries per card line**:
  1. `content://sms/inbox` — selection `address = phone AND date >= start-of-month AND body LIKE '[Web발신]%'`. Body is whitespace-stripped, `자동결제`/`자동 결제` rewritten to `승인`, then `AMOUNT_PATTERN`(승인) or `CANCEL_AMOUNT_PATTERN`(취소) matched. Cancellations stored as **negative** amounts so per-group totals net out.
  2. `content://im/chat` — Samsung RCS / GSMA MaaP store, `content_type = application/vnd.gsma.openrichcard.v1.0+json` (`addRcsApprovals`). Body is JSON; `extractRichCardTexts` does DFS over the layout tree collecting `widget == "TextView"` text values, then `findAmountAfterLabel(texts, "금액")` extracts the amount. RCS cards are **always approvals** (취소 RCS 카드 미관측 — 취소는 SMS 경로로만 들어옴). The persisted `body` is reformatted into a single human-readable line (`[Web발신] <카드> 승인 <거래구분> <금액>원 <거래시간> <사용처> 누적 <누적금액>`) so the existing Compose body-rendering code works without changes.
  - Both paths feed the same `groupMap`. RCS access from this app uses only `READ_SMS` (verified on Samsung Flip via `probeRcsAccess`). See `docs/rcs-investigation-2026-04-27.md`.
- `MainActivity.kt` — Compose `CardApprovalScreen`. Owns the only refresh path users hit (top-bar refresh + initial load + widget-triggered refresh via `intent.getBooleanExtra("refresh", ...)`). Permission is requested on first composition.
- `widget/CardWidgetProvider.kt` — `AppWidgetProvider`. Renders `widget_total` and the first 5 entries of `widget_groups` into `res/layout/widget_card.xml`. Custom broadcast action `com.example.mycard.WIDGET_REFRESH` runs `SMSReader` directly from the widget's refresh button, independent of the Activity. Parses `widget_groups` with regex (not Gson).
- `CardRefreshWorker.kt` — `CoroutineWorker` that runs the same refresh and additionally writes a timestamped `card_approval_yyyyMMdd_HHmmss.txt` to `getExternalFilesDir(null)` and `getExternalFilesDir(DIRECTORY_DOCUMENTS)`. **Not scheduled by anything currently** — `MainActivity.kt` imports `WorkManager` / `PeriodicWorkRequestBuilder` but never enqueues it. Wire from `MainActivity` if needed.
- `SettingsActivity.kt` — Compose form that persists `cardGroup` and `memo` to `mycard_prefs`. Launched from the `⋮` menu in `MainActivity`.

### Permissions / silent-failure traps

Only `READ_SMS` is declared. `MainActivity` requests it at runtime; **the Widget and Worker assume it's already granted** and will silently produce empty results otherwise. If you add another entry point, request the permission there too or document the assumption.

## Quirks to preserve, not "fix"

- `MainActivity.kt:208` uses `getSharedPreferences("l\`", ...)` — a typo prefs name, not `mycard_prefs`. The top-bar refresh-button branch writes to a stray prefs file. Don't fix as a side-effect of unrelated work; only touch when explicitly asked.
- Korean comments contain `위젷` (typo of `위젯`) repeated across files. Leave it.

## Conventions

- Match the surrounding language for prose / comments / UI strings — Korean dominates this app.
- 작업 관련 문서(분석, 수정 기록, 테스트 방법 등)는 이 프로젝트의 `D:\workspace\mycard\MyCard\docs\`에 저장합니다. 워크스페이스 루트나 `app/src/` 아래에는 만들지 않습니다.
- 파일명 끝에 작성일을 `YYYY-MM-DD` 형식으로 붙입니다 (같은 날 충돌 시에만 `-HHMM` 추가). 기존 문서를 큰 폭으로 갱신할 때는 새 파일을 만들지 않고 기존 파일을 수정합니다.

## MyCard — "파싱하자" 워크플로우

사용자가 알림 기반 파싱 룰을 추가하려고 할 때 따르는 정해진 절차. 단말이 바뀌어도 동일하게 적용.

**대화 절차:**
1. 사용자가 "파싱하자"라고 하면 → "몇 시 알림인가요?"로 시각을 묻는다.
2. 사용자가 시각(예: "13:10")을 답하면 → `adb pull /sdcard/Documents/MyCard/raw_notifications_all.jsonl`로 받아서 그 시각 ±5분 알림을 추출해 보여준다. ts→KST는 `TZ=Asia/Seoul date -d @<sec> +%H:%M:%S`.
3. → "어느 카드 알림인가요?" 카드명을 묻는다 (예: "네이버 현대카드"). 카드사 앱과 페이 앱이 섞여 있을 수 있고, 페이 앱은 카드 식별 불가라 룰 대상으로 부적합.
4. 룰 작성: title/body에서 `(?<amount>...)` `(?<merchant>...)` named group으로 잡는 regex. **반드시 Python으로 결제 매칭 + 같은 패키지 광고 비매칭 둘 다 검증**한 >뒤 사용자에게 보여주고 승인 받기.
5. 배포(아래 절차) → 사용자에게 "메뉴 3(업데이트) 누르세요" 안내.

**`card_filters.json` 배포 절차 (중요 — owner 유지):**
- `adb push`는 row owner를 `com.android.shell`로 바꾼다. MyCard는 minSdk 34 + scoped storage라 다른 owner의 `application/json` row는 query에서 제외 — 즉 **단순 `adb push`는 작동하지 않는다**.
- 올바른 흐름:
  1. row 확인: `adb shell "content query --uri content://media/external/file --projection _id:owner_package_name:_size --where \"_display_name='card_filters.json'\""`
  2. owner=`com.example.mycard` row가 없으면: 사용자에게 메뉴 3 한 번 눌러달라고 부탁 → `CardFilterStore.load()` 안의 bootstrap이 빈 row를 자기 owner로 생성한다 (`IS_PENDING=0` 명시 필수 — 없으면 Samsung One UI에서 query 시 자기가 만든 row를 다시 못 찾는다).
  3. row id 확인 후: `adb shell "content write --uri content://media/external_primary/file/<row_id>" < /tmp/card_filters.json`. 이 방식은 file 내용만 갱신하고 owner는 유지된다.
  4. Download/ 등 잘못된 위치로 부수적으로 push된 row가 생겼으면 `content delete --where "_id=<n>"`로 즉시 정리.

**필터 파일 스키마** (`MyCard/app/src/main/java/com/example/mycard/parser/CardFilter.kt`):
- `CardFiltersFile { version, updated_at, filters: List<CardFilter> }`
- `CardFilter { id, card_company, package, match: { title_regex, body_regex, type }, examples, added_at, added_from_ts }`
- `match.type`은 `"approval"` 또는 `"cancel"` (cancel이면 amount가 음수로 저장).
- 파싱 흐름은 `CardParser.parse()` → `CardFilterStore.byPackage(pkg)` → 각 후보별 `tryMatch()` 순으로 첫 매칭 반환.

**디버깅:**
- 모든 핵심 분기에 로그가 있다 (`CardFilterStore`, `CardParser`, `UpdateAction`, `CardNotifListener`, `RawDump`, `RawDumpAll`).
- `adb logcat -d --pid=$(adb shell pidof com.example.mycard) -s CardFilterStore CardParser UpdateAction CardNotifListener RawDump RawDumpAll` 한 줄로 전체 흐름 추>적 가능.
- "재구성 N/실패 N" 결과만 보고 파싱이 안 됐을 때, 가장 자주 빠지는 곳은 `CardFilterStore: ensureFileUri: no row matched`(MediaStore에서 row 못 봄) — 100% owner 격
리 또는 `IS_PENDING` 문제.

**Raw data 아카이브 (`docs/data/<카드사>.jsonl`):**
- 파싱 룰을 새로 만들 때 사용한 알림은 **그대로(전체 raw 객체) `MyCard/docs/data/<카드사>.jsonl`에 한 줄씩 저장**한다. 같은 카드사의 다른 카드(예: 신한 8423/9999) 도 하나의 파일에 누적 append.
- 목적: ① 룰 회귀 테스트 코퍼스 ② 카드사 알림 포맷 변경 추적 ③ 같은 패키지의 광고/청구서 등 비매칭 케이스도 함께 모아 negative 검증 자료로 사용. 그래서 결제뿐 아니라 광고/공지도 포함해 둘 가치가 있다.
- 객체 형식은 `raw_notifications_all.jsonl` 내부 객체 그대로 (`pkg/ts/title/text/bigText/subText/category/channelId/rawExtras`). 한 줄 = 한 객체 (pretty-print 금지).
- 파일명은 카드사 단위 (`신한카드.jsonl`, `현대카드.jsonl`, `삼성카드.jsonl` …). 페이 앱(`com.samsung.android.spay` 등)은 카드 식별이 안 되므로 별도 보관할 가치 없음.
- 추가 절차: `adb pull /sdcard/Documents/MyCard/raw_notifications_all.jsonl` (필요시 whitelist-only `raw_notifications.jsonl`도) → 해당 패키지 객체만 dedupe (`ts|title|text` 키)해 append. 단말 dump는 multi-line pretty JSON이므로 `JSON.parse`가 아닌 brace-balanced 스캐닝으로 객체 분리.

## MyCard — "main에서 가져오자" 워크플로우

사용자가 "main에서 (특정 commit) 가져오자" 또는 "최신 main 변경 가져오자"라고 할 때 따르는 절차.

**원칙:**
1. **파일 위치는 우리 쪽 구조 유지** — main 쪽이 옛 경로(`ui/theme/SMSReader.java` 등)를 수정한 commit을 들고 있어도 우리 `sms/` 패키지로 옮긴 건 되돌리지 않는다. modify/delete 충돌이 나면 `git rm`으로 삭제 측을 채택하고, 의미 있는 동작 변화만 발췌해 우리 쪽 파일에 수동 적용.
2. **UI 변경은 두 곳 모두에 적용** — main의 commit이 SMS 보기(`MainActivity.kt`의 `CardApprovalScreen`) UI를 바꿨다면, 알림 기반 보기(`notif/NotificationBasedCardActivity.kt`)에도 **동일한 시각적 변경을 손으로 옮긴다**. 두 화면은 데이터 소스만 다르고 카드/Row 레이아웃은 통일되어야 한다는 invariant.
   - 예: acfda71에서 `Row { Column { date, body, type } | Text(amount) }` → `Row { Column(weight=1f) { date, body } | Column(End) { type(bodySmall), amount(bodyMedium) } }`로 바꾼 것을 NotificationBasedCardActivity에도 같이 적용.
   - 알림 뷰에 안 맞는 SMS 전용 helper(예: `extractBodyText` — RCS JSON 본문 정리용)는 옮기지 않는다. 알림 뷰는 이미 `merchant`가 정제되어 있음.
3. **순서대로 cherry-pick** — `git cherry-pick <oldest>^..<newest>` 한 번으로 범위 적용. 충돌은 발생 즉시 멈추고 사용자에게 어느 쪽을 채택할지 보여주고 진행. 자동 머지된 파일은 conflict marker만 빠르게 검사하고 통과.
4. **import 충돌은 기본적으로 HEAD 측을 유지** — 우리 라인이 더 많은 기능(coroutines, SimpleDateFormat 등)을 쓰고 있을 가능성이 높음. 미사용 import가 섞여도 컴파일은 깨지지 않음. 새 기능 호출 자체(예: `scheduleDailyRefresh()`)는 main 측을 채택.
5. **빌드 확인은 명시 요청 시에만 돌린다.** 기본은 cherry-pick 결과만 보고하고, 사용자가 "빌드해줘"라고 하면 `./gradlew assembleDebug`로 확인.
