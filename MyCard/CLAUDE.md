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

**Ship 시 새 feature branch 생성 규칙:**
- `/ship`을 실행할 때 현재 브랜치가 `sach_dev`이면 **sach_dev에 직접 push/commit 하지 않는다**. 먼저 작업 내용을 옮길 새 feature branch(예: `feature/<topic>`)를 만들고 거기서 PR을 sach_dev로 올린다.
- 절차:
  1. 현재 sach_dev에 staged/uncommitted 변경이 있으면 임시로 stash하거나 그대로 둔다.
  2. `git checkout -b feature/<topic> sach_dev` (필요 시 마지막 N개 commit을 옮길 경우 `git reset --keep <base>` 등으로 sach_dev를 되돌려 둔다 — 사용자 승인 후).
  3. `/ship`이 PR을 만들 때 base는 `sach_dev`.
- 이미 sach_dev에 commit이 들어가 있다면 사용자에게 보고하고 — (a) 그대로 두고 다음 작업부터 규칙 적용 (b) cherry-pick으로 새 branch에 옮기고 sach_dev를 reset 중 선택을 받는다. 임의로 sach_dev를 reset하지 않는다.

## What this app does

**알림 기반 아키텍처** (commit `1bb9796` 이후). 카드사 앱의 푸시 알림을 `NotificationListenerService`로 수집해 `card_filters.json`의 regex 룰로 금액/가맹점을 파싱하고, Room DB에 적재한 뒤 카드(`card_company`)별로 이번 달 승인액 − 취소액을 합산해 다음에 표시한다:
- a Compose Activity UI (`MainActivity`) — 월 이동(◀▶), 카드별 월 한도/여유분,
- a home-screen `AppWidget` (`CardWidgetProvider`).

과거의 SMS/RCS 파싱(`sms/SMSReader.java`)은 **더 이상 집계에 쓰이지 않는다** — `SMSReader.SmsGroup`/`SmsItem` 데이터 클래스만 결과 타입으로 재사용된다. UI strings, comments, and log text are in Korean.

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

### 데이터 흐름

```
카드사 앱 알림
  → notif/CardNotificationListener (NotificationListenerService)
      ├─ RawDumpAll  : 모든 알림 → raw_notifications_all_<YYYY>_<MM>.jsonl (월 단위, 파싱 룰 작성용 원본)
      ├─ Blacklist   : 해당 pkg면 여기서 중단
      ├─ Whitelist   : 포함된 pkg만 CardParser.parse()
      ├─ Room DB     : notifications.db (같은 pkg/title/text/bigText가 5초 내 중복이면 skip)
      ├─ RawDump     : DB에 들어간 건 → raw_notifications_<YYYY>.jsonl (연 단위, rebuildFromRaw 원본)
      └─ 파싱 성공 시 SmsReceiver.ACTION_SMS_UPDATED 브로드캐스트 → 열려 있는 UI 갱신
```

집계는 `notif/NotifCardReader.kt`의 `readNotifCardGroups(context, monthOffset)` 하나로 통일되어 있다 (Activity·Widget·Worker 모두). DB의 `amount IS NOT NULL` 행을 월 범위로 읽어 `filterId → card_company`(없으면 pkg → card_company, 그래도 없으면 pkg)로 그룹핑한다.

### 파일 저장소: `/sdcard/Documents/MyCard/` (`storage/AppStorage`)

`MANAGE_EXTERNAL_STORAGE`(전체 파일 접근)로 **`java.io.File` 직접 읽기/쓰기**. MediaStore를 쓰지 않는다.
- `card_filters.json` — 파싱 룰 (`CardFilterStore`). **이 파일이 없으면 아무 알림도 파싱되지 않아 메인 리스트가 비어 보인다.**
- `whitelist.txt` / `blacklist.txt` — 파일이 없을 때만 `Whitelist.DEFAULT_PACKAGES`로 생성. 이후엔 파일이 우선.
- `raw_notifications_<YYYY>.jsonl`, `raw_notifications_all_<YYYY>_<MM>.jsonl` — 위 흐름 참고. 둘 다 pretty-print JSON 객체를 이어 붙인 형식(한 줄 1객체 아님).

### Shared state: `SharedPreferences("mycard_prefs", MODE_PRIVATE)`

Activity, Worker, Widget이 공유. Keys:
- `widget_total: Long`, `widget_today_count: Int`, `widget_total_count: Int` — 위젯 표시값.
- `widget_groups: String` — hand-rolled JSON `[{"id":"X","total":1234},...]`. 위젯이 regex로 파싱.
- `card_limits: String` — 카드별 월 한도 JSON 맵 (`limit/CardLimitStore`).
- `memo: String`, `cardGroup: String` — `SettingsActivity`가 저장. `cardGroup`은 SMS 시절 설정(`phone,id[,last4]`)으로, 지금은 `SmsReceiver`의 발신번호 판별과 위젯 그룹 정렬 순서에만 쓰인다.

**If you add a new refresh path, it must update `widget_total`/`widget_groups`(+count 키) and refresh the widget.** 이미 거의 같은 블록이 5곳에 있다 (`MainActivity.kt`의 `LaunchedEffect(shouldRefresh)`·`LaunchedEffect(groups, monthOffset)`, `SmsReceiver.refreshAndNotify`, `CardRefreshWorker`). 새로 추가하기 전에 `SmsReceiver.refreshAndNotify()` 재사용 또는 helper 추출을 먼저 고려.

### Components

> **SMSReader.java 위치:** `app/src/main/java/com/example/mycard/sms/SMSReader.java` (`sms/` 패키지). 과거에는 `ui/theme/SMSReader.java`였으나 이전 작업에서 의도적으로 옮긴 것이다. **다시 `ui/theme/`로 되돌리지 말 것.** main 브랜치 등 다른 라인에 옛 경로(`ui/theme/SMSReader.java`)를 수정한 commit이 남아 있어 cherry-pick/merge 시 modify/delete 충돌이 날 수 있다. 그런 commit의 SMSReader 변경은 **현재 `sms/SMSReader.java`에 더 정교한 RCS 파싱(`addRcsApprovals` 등)이 이미 있어 대부분 무가치**하므로, 충돌 시 옛 경로 파일을 다시 만들지 말고 `git rm`으로 삭제 측을 선택하면 된다. 의미 있는 동작 변화가 있으면 그것만 발췌해 `sms/SMSReader.java`에 수동 적용.

- `notif/CardNotificationListener.kt` — 알림 수집 진입점 (위 데이터 흐름).
- `parser/CardFilterStore.kt` / `parser/CardParser.kt` — 룰 로드(패키지별 인덱스, 읽기 실패 시 캐시하지 않고 다음 호출에 재시도) / 파일 순서대로 첫 매칭 반환.
- `notif/UpdateAction.kt` — `rebuildFromRaw()`: 필터·화이트/블랙리스트·RawDump 캐시 무효화 → DB `clear()` → `raw_notifications_<YYYY>.jsonl` 전체를 최신 필터로 재파싱. `manually_overridden` 객체는 재파싱하지 않고 저장된 amount/merchant 유지. ⋮ → 업데이트.
- `MainActivity.kt` — Compose `CardApprovalScreen`. 월 이동, 카드 한도 다이얼로그(`ui/CardLimitDialog`), 알림 접근/전체 파일 접근 권한 스낵바. ⋮ 메뉴: 설정 / 카드 한도 / 알림 로그 / 업데이트 / 항목 추가 / 알림 기반 보기. `onCreate`에서 `CardRefreshWorker`를 **매일 01:00 periodic으로 enqueue**한다.
- `notif/NotificationBasedCardActivity.kt` — 알림 기반 보기. 항목 수정/삭제 (`RawDump.updateByTs`/`removeByTs` 후 재구성).
- `notif/NotificationListActivity.kt` — 알림 로그. 화이트리스트 토글, 블랙리스트 추가, 권한/배터리 최적화 배너. `monthRange()`/`monthLabel()`도 여기 정의.
- `notif/ManualEntryActivity.kt` — 수동 항목 추가 (pkg `com.example.mycard.manual`로 RawDump에 append 후 rebuild).
- `widget/CardWidgetProvider.kt` — `widget_*` prefs를 읽어 `widget_card.xml`에 최대 5개 그룹 표시. `WIDGET_REFRESH` 브로드캐스트 → `SmsReceiver.refreshAndNotify()`.
- `CardRefreshWorker.kt` — 같은 갱신 + `card_approval_yyyyMMdd_HHmmss.txt`를 앱 external files dir에 기록.
- `SmsReceiver.kt` — `SMS_RECEIVED` 시 `cardGroup` 발신번호와 일치하면 2초 뒤 `refreshAndNotify()`. 집계 자체는 알림 DB 기준.
- `SettingsActivity.kt` — `memo`/`cardGroup` 저장 (memo는 MediaStore `Documents/mycard_memo.txt`에도 기록).
- `sms/SMSReader.java`, `sms/RcsParser.java` — 구세대 SMS/RCS 파싱. 현재 호출되지 않음 (`docs/rcs-investigation-2026-04-27.md` 참고).

### Permissions / silent-failure traps

- **알림 접근 권한(Notification Listener)** — 집계 전체가 여기에 달려 있다. 꺼지면 에러 없이 새 결제가 그냥 안 쌓인다. `MainActivity`가 스낵바로 안내.
- **`MANAGE_EXTERNAL_STORAGE`** — 없으면 `card_filters.json` 등을 못 읽어 파싱 0건.
- `READ_SMS`/`RECEIVE_SMS` — `MainActivity`가 런타임 요청하며, 아직 이 권한 여부로 데이터 로드를 게이트한다 (알림 기반 집계에는 실제로 필요 없음).

## Quirks to preserve, not "fix"

- Korean comments contain `위젷` (typo of `위젯`) repeated across files. Leave it.

## Conventions

- Match the surrounding language for prose / comments / UI strings — Korean dominates this app.
- 작업 관련 문서(분석, 수정 기록, 테스트 방법 등)는 이 프로젝트의 `D:\workspace\mycard\MyCard\docs\`에 저장합니다. 워크스페이스 루트나 `app/src/` 아래에는 만들지 않습니다.
- 파일명 끝에 작성일을 `YYYY-MM-DD` 형식으로 붙입니다 (같은 날 충돌 시에만 `-HHMM` 추가). 기존 문서를 큰 폭으로 갱신할 때는 새 파일을 만들지 않고 기존 파일을 수정합니다.

## MyCard — "파싱하자" 워크플로우

사용자가 알림 기반 파싱 룰을 추가하려고 할 때 따르는 정해진 절차. 단말이 바뀌어도 동일하게 적용.

**대화 절차:**
1. 사용자가 "파싱하자"라고 하면 → "몇 시 알림인가요?"로 시각을 묻는다.
2. 사용자가 시각(예: "13:10")을 답하면 → `adb pull /sdcard/Documents/MyCard/raw_notifications_all_<YYYY>_<MM>.jsonl`(해당 월 파일)로 받아서 그 시각 ±5분 알림을 추출해 보여준다. ts→KST는 `TZ=Asia/Seoul date -d @<sec> +%H:%M:%S`.
3. → "어느 카드 알림인가요?" 카드명을 묻는다 (예: "네이버 현대카드"). 카드사 앱과 페이 앱이 섞여 있을 수 있고, 페이 앱은 카드 식별 불가라 룰 대상으로 부적합.
4. 룰 작성: title/body에서 `(?<amount>...)` `(?<merchant>...)` named group으로 잡는 regex. **반드시 Python으로 결제 매칭 + 같은 패키지 광고 비매칭 둘 다 검증**한 >뒤 사용자에게 보여주고 승인 받기.
5. 배포(아래 절차) → 사용자에게 "⋮ 메뉴 → 업데이트 누르세요" 안내.

**`card_filters.json` 소스 관리 (git):**
- 소스 파일은 **`MyCard/config/card_filters.json`** 에서 관리한다. 이 파일이 유일한 원본이다.
- 수정 절차:
  1. `config/card_filters.json` 편집
  2. Python으로 변경된 regex 검증 (매칭 케이스 + 비매칭 케이스 모두)
  3. 문제 없으면 commit
  4. 아래 배포 절차로 단말에 반영

**`card_filters.json` 배포 절차:**
- 앱이 `MANAGE_EXTERNAL_STORAGE` + `java.io.File`로 직접 읽으므로 **단순 `adb push`로 배포된다** (2026-10-17 Fold7에서 확인. 예전 MediaStore owner 유지 / `content write` 절차는 더 이상 필요 없음).
  1. `MSYS_NO_PATHCONV=1 adb -s <serial> push config/card_filters.json /sdcard/Documents/MyCard/card_filters.json`
  2. 앱 재시작 후 로그 확인: `CardFilterStore: load: loaded filters=<N>` (캐시가 있으면 `am force-stop com.example.mycard` 후 재실행)
  3. 앱에서 **⋮ 메뉴 → 업데이트** → rebuildFromRaw로 기존 알림에 소급 적용.
- 증상 "메인 리스트가 비어 있음" → 가장 먼저 `CardFilterStore: readShared: ... exists=false` 여부 확인. 파일이 없으면 위 절차로 push.

**필터 파일 스키마** (`MyCard/app/src/main/java/com/example/mycard/parser/CardFilter.kt`):
- `CardFiltersFile { version, updated_at, filters: List<CardFilter> }`
- `CardFilter { id, card_company, package, match: { title_regex, body_regex, type }, examples, added_at, added_from_ts }`
- `match.type`은 `"approval"` 또는 `"cancel"` (cancel이면 amount가 음수로 저장).
- 파싱 흐름은 `CardParser.parse()` → `CardFilterStore.byPackage(pkg)` → 각 후보별 `tryMatch()` 순으로 첫 매칭 반환.

**디버깅:**
- 모든 핵심 분기에 로그가 있다 (`CardFilterStore`, `CardParser`, `UpdateAction`, `CardNotifListener`, `RawDump`, `RawDumpAll`).
- `adb logcat -d --pid=$(adb shell pidof com.example.mycard) -s CardFilterStore CardParser UpdateAction CardNotifListener RawDump RawDumpAll` 한 줄로 전체 흐름 추>적 가능.
- "재구성 N/실패 N" 결과만 보고 파싱이 안 됐을 때, 가장 자주 빠지는 곳은 `CardFilterStore: readShared: ... exists=false`(필터 파일 없음) 또는 `canRead=false`(전체 파일 접근 권한 꺼짐).

**Raw data 아카이브 (`docs/data/<카드사>.jsonl`):**
- 파싱 룰을 새로 만들 때 사용한 알림은 **그대로(전체 raw 객체) `MyCard/docs/data/<카드사>.jsonl`에 한 줄씩 저장**한다. 같은 카드사의 다른 카드(예: 신한 9999/9999) 도 하나의 파일에 누적 append.
- 목적: ① 룰 회귀 테스트 코퍼스 ② 카드사 알림 포맷 변경 추적 ③ 같은 패키지의 광고/청구서 등 비매칭 케이스도 함께 모아 negative 검증 자료로 사용. 그래서 결제뿐 아니라 광고/공지도 포함해 둘 가치가 있다.
- 객체 형식은 `raw_notifications_all_<YYYY>_<MM>.jsonl` 내부 객체 그대로 (`pkg/ts/title/text/bigText/subText/category/channelId/rawExtras`). 한 줄 = 한 객체 (pretty-print 금지).
- 파일명은 카드사 단위 (`신한카드.jsonl`, `현대카드.jsonl`, `삼성카드.jsonl` …). 페이 앱(`com.samsung.android.spay` 등)은 카드 식별이 안 되므로 별도 보관할 가치 없음.
- 추가 절차: `adb pull /sdcard/Documents/MyCard/raw_notifications_all_<YYYY>_<MM>.jsonl` (필요시 whitelist-only `raw_notifications_<YYYY>.jsonl`도) → 해당 패키지 객체만 dedupe (`ts|title|text` 키)해 append. 단말 dump는 multi-line pretty JSON이므로 `JSON.parse`가 아닌 brace-balanced 스캐닝으로 객체 분리.

**카드 룰 문서화 (`docs/card-filter-<카드사>-<YYYY-MM-DD>.md`):**
- 룰을 **신규 추가**하거나 **기존 룰을 수정**할 때마다 반드시 문서를 작성한다.
- 파일명: `docs/card-filter-<카드사식별자>-<YYYY-MM-DD>.md`. 같은 날 같은 카드사에 변경이 더 생기면 새 파일을 만들지 않고 기존 파일을 수정한다.
- 문서에 포함해야 할 항목:
  1. **대상 알림** — 패키지명, 카드명, 대표 ts
  2. **알림 구조** — title/text/bigText 예시 (실제 raw 텍스트)
  3. **추가/변경된 필터** — filter id, title_regex, body_regex, type
  4. **검증 표** — 매칭 케이스(amount·merchant 확인) + 비매칭 케이스(같은 패키지 광고 등)
  5. **Raw 데이터 아카이브** — `docs/data/<카드사>.jsonl`에 몇 건 추가했는지
  6. (수정인 경우) **변경 경위** — 이전 포맷과 무엇이 달라졌는지
- 기존 룰을 수정한 경우: 기존 문서를 업데이트하되, 수정 이력(날짜·변경 내용)을 문서 하단에 추가한다.

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

## Room DB 직접 수정 금지

**`notifications.db`를 `adb shell` + `run-as cat >` 또는 `adb push` 로 직접 덮어쓰는 것은 절대 하지 않는다.**

**이유**: Windows 환경에서 바이너리 파일을 shell 파이프로 push하면 개행 변환(`\n` → `\r\n`) 등으로 SQLite 파일이 손상된다. Room은 시작 시 DB 무결성을 검사하고, 손상 감지 시 **자동으로 DB를 삭제하고 빈 DB를 재생성**한다 — 모든 알림 이력이 날아간다.

**올바른 복구/소급 적용 방법:**
1. `card_filters.json` 배포 (위 배포 절차 참고)
2. 앱에서 **⋮ 메뉴 → "업데이트"** 탭
3. `UpdateAction.rebuildFromRaw()` 가 `raw_notifications_<year>.jsonl` (화이트리스트 파일)을 읽어 DB 전체를 재파싱 — 새 필터가 과거 알림에도 소급 적용됨

이미 파싱된 특정 레코드의 `amount`/`merchant` 값을 수동 교정하고 싶더라도, DB 파일 직접 push 대신 rebuildFromRaw를 쓴다. rebuildFromRaw는 모든 레코드를 최신 필터 기준으로 재파싱하므로 결과가 더 정확하다.

## Development rules
1. MyCard 이하 폴더만 수정한다.
2. 개발 branch의 main은 "sach_dev" 이다. PR 생성시에도 sach_dev branch로 merge하는 것을 목표로 한다.
3. MainActivity.kt의 수정은 최소한다.
