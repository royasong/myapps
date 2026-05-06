# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This repo holds **two independent Android Gradle projects** under one git root. They share no build, no source, no version catalog — each has its own `gradlew`, `settings.gradle.kts`, `gradle/libs.versions.toml`, and single `:app` module. Pick the right project before running anything.

- `arin/` — `com.arin.app`, a personal "SMS quick-send / call" launcher with XML layouts.
  - Kotlin 1.9.0, AGP 8.6.1, JVM target 1.8, `compileSdk 35`, `minSdk 33`.
  - Uses View binding + Compose BOM 2024.04.01 (Compose only used for theme/preview; real UI is XML).
  - 3rd-party: `com.github.yukuku:ambilwarna` (color picker).
- `MyCard/` — `com.example.mycard`, reads card-approval SMS, totals them, exposes a home-screen widget.
  - Kotlin 2.0.21 + Compose Compiler plugin, AGP 8.11.2, JVM target 11, `compileSdk 36`, `minSdk 34`.
  - Compose BOM 2024.09.00, WorkManager, DocumentFile, Gson 2.11.0.

## Common commands

Run inside the project directory you want to touch (`cd arin` or `cd MyCard`).

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # install on connected device/emulator
./gradlew test                   # JVM unit tests (src/test)
./gradlew connectedAndroidTest   # instrumented tests (needs device)
./gradlew lint                   # Android lint
./gradlew clean
```

Single-test runs:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mycard.ExampleUnitTest.someMethod"
./gradlew :app:testDebugUnitTest --tests "com.arin.app.ExampleUnitTest"
```

Note: `arin/local.properties` is checked in (contains `sdk.dir`); `MyCard/` ignores it. If a build complains about SDK location in `MyCard/`, create `MyCard/local.properties` with `sdk.dir=...`.

## arin/ — high-level architecture

A 3-Activity AppCompat app driven by `MainActivity` → `SettingActivity` / `EditSmsActivity` via `ActivityResultLauncher`. The "from" string extra (`"setting"` / `"editsms"`) tells `MainActivity` which subset of UI state to refresh on return.

State is persisted as **plain files in `context.filesDir`**, not SharedPreferences:
- `bg_color.txt`, `btn_color.txt` — color int (as decimal string), written by `SettingActivity`, read by `MainActivity.getColor()`.
- `sms.txt` — 4 quick-send messages, one per line. Read/written through `SmsTextValue` (singleton; `initize(path, context)` must run before `getText()`).
- `arin_bg.png` — gallery-picked background bitmap (saved via `BitmapFactory` + `FileOutputStream`).

Phone numbers (`mom_number_`, `dad_number_`) are hard-coded in `MainActivity`. SMS sending uses the deprecated `SmsManager.getDefault()` API and requires `SEND_SMS` + `CALL_PHONE` runtime permissions (manifest declares them but no runtime request flow exists for SEND_SMS/CALL_PHONE — only `READ_MEDIA_IMAGES` is requested in `SettingActivity`).

## MyCard/ — high-level architecture

Reads the device's SMS inbox (`content://sms/inbox`), filters this-month `[Web발신]` card-approval messages, groups them by user-configured card identifiers, and surfaces totals in both a Compose UI and a home-screen `AppWidget`.

Pieces:
- `SMSReader.java` (under `ui/theme/` — misplaced but that's the real path) is the parsing core. `readCardApprovalGrouped(context)` returns `List<SmsGroup>`. It reads the user's "cardGroup" string from SharedPreferences `mycard_prefs`, splits by newline, and for each line (`phone,id[,keyword]`) queries SMS WHERE `address = phone AND date >= start-of-month AND body LIKE '[Web발신]%'`. Body is whitespace-stripped, `자동결제`/`자동 결제` rewritten to `승인`, then matched against `AMOUNT_PATTERN` (승인) or `CANCEL_AMOUNT_PATTERN` (취소). Cancellations stored as **negative** amounts so totals net out.
- `MainActivity.kt` — Compose `CardApprovalScreen`. Owns the only refresh path users hit (top-bar refresh button + initial load). Whenever `groups` changes it **also writes widget state** to `mycard_prefs`: `widget_total: Long` and `widget_groups: String` (hand-rolled JSON like `[{"id":"X","total":1234},...]`). Then calls `appWidgetManager.notifyAppWidgetViewDataChanged(...)`.
- `widget/CardWidgetProvider.kt` — `AppWidgetProvider`. Reads `widget_total` / `widget_groups` from `mycard_prefs` and renders into `widget_card.xml`. Has a custom broadcast action `com.example.mycard.WIDGET_REFRESH` that re-runs `SMSReader` directly from the widget's refresh button (independent of the Activity). Parses `widget_groups` with regex, not Gson.
- `CardRefreshWorker.kt` — `CoroutineWorker` that does the same refresh + writes a timestamped `card_approval_yyyyMMdd_HHmmss.txt` to `getExternalFilesDir(...)` and the DOCUMENTS subdir. **Not currently scheduled** by anything in this codebase; if you wire it up, use `WorkManager` from `MainActivity` (the import is already there).
- `SettingsActivity.kt` — Compose form that writes `cardGroup` and `memo` strings into `mycard_prefs`. The cardGroup textarea expects one entry per line, comma-separated `phone,id` (or `phone,id,keyword`).

Cross-cutting invariant: **`mycard_prefs` is the single source of truth** shared between Activity, Worker, and Widget. If you add a new refresh path, it must update both `widget_total` and `widget_groups` and call `notifyAppWidgetViewDataChanged` — there are already 4 copies of this update block in `MainActivity.kt`; consider extracting before adding a 5th.

Permissions: `READ_SMS` only. The Activity requests it on first composition; the Widget and Worker assume it's already granted (they will silently produce empty results otherwise).

Known quirks worth preserving awareness of:
- `MainActivity.kt:208` uses `getSharedPreferences("l\`", ...)` (a typo, not `mycard_prefs`). The refresh-button branch writes to a stray prefs file. Fix only if explicitly asked.
- Comments in MyCard say "위젷" instead of "위젯" — Korean typo, repeated. Don't "fix" it as a side effect of unrelated work.

## Conventions

- Korean is used freely in UI strings, log tags, and comments. Keep it.
- Don't add cross-project Gradle wiring (root `settings.gradle.kts`, version catalog sharing, etc.) unless asked — these two apps are intentionally independent.
- Prefer editing the existing file at its existing path over relocating. `SMSReader.java` lives under `ui/theme/` for historical reasons; moving it is a separate task.

## MyCard — "파싱하자" 워크플로우

사용자가 알림 기반 파싱 룰을 추가하려고 할 때 따르는 정해진 절차. 단말이 바뀌어도 동일하게 적용.

**대화 절차:**
1. 사용자가 "파싱하자"라고 하면 → "몇 시 알림인가요?"로 시각을 묻는다.
2. 사용자가 시각(예: "13:10")을 답하면 → `adb pull /sdcard/Documents/MyCard/raw_notifications_all.jsonl`로 받아서 그 시각 ±5분 알림을 추출해 보여준다. ts→KST는 `TZ=Asia/Seoul date -d @<sec> +%H:%M:%S`.
3. → "어느 카드 알림인가요?" 카드명을 묻는다 (예: "네이버 현대카드"). 카드사 앱과 페이 앱이 섞여 있을 수 있고, 페이 앱은 카드 식별 불가라 룰 대상으로 부적합.
4. 룰 작성: title/body에서 `(?<amount>...)` `(?<merchant>...)` named group으로 잡는 regex. **반드시 Python으로 결제 매칭 + 같은 패키지 광고 비매칭 둘 다 검증**한 뒤 사용자에게 보여주고 승인 받기.
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
- `adb logcat -d --pid=$(adb shell pidof com.example.mycard) -s CardFilterStore CardParser UpdateAction CardNotifListener RawDump RawDumpAll` 한 줄로 전체 흐름 추적 가능.
- "재구성 N/실패 N" 결과만 보고 파싱이 안 됐을 때, 가장 자주 빠지는 곳은 `CardFilterStore: ensureFileUri: no row matched`(MediaStore에서 row 못 봄) — 100% owner 격리 또는 `IS_PENDING` 문제.

## Developing
- MyCard 이하 디렉토리만 수정 가능
- sach_dev branch를 main으로 사용
- MainActivity.kt 의 수정사항은 최소화한다.
- main으로 부터 sach_dev로 개발된 feature들을 가져온다.

