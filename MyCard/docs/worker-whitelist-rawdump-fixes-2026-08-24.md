# 위젯 초기화 · 롯데카드 화이트리스트 · RawDump 증분 기록 수정 (2026-08-24)

전체 소스 파악 중 발견한 구조적 결함 3건을 수정했다. 세 건 모두 조용히 실패하는 종류라
증상이 사용자에게 "금액이 안 맞는다"로만 보이던 것들이다.

## ① `CardRefreshWorker`가 매일 새벽 1시에 위젯 합계를 0으로 덮어쓰던 문제

### 경위

`MainActivity.onCreate()` → `scheduleDailyRefresh()`가 `CardRefreshWorker`를 매일 01:00에
등록한다. 그런데 Worker는 구세대 SMS 파이프라인인 `SMSReader.readCardApprovalGrouped()`를
호출하고 있었다.

`SMSReader`는 `mycard_prefs`의 `cardGroup`(`전화번호,id[,뒷4자리]` 줄 목록)을 읽어 그 설정에
있는 발신번호만 조회한다. 현세대(알림 기반) 사용자는 `cardGroup`을 쓰지 않으므로 이 값이 비어
있고, 그러면 `cardGroups = new String[0]` → 그룹 0개 → `grandTotal = 0`이 된다.

Worker는 그 결과를 그대로 `widget_total` / `widget_groups` / `widget_today_count` /
`widget_total_count`에 쓰고 위젯을 갱신한다. 즉 **매일 새벽 1시에 위젯이 0원으로 초기화**되고,
다음 카드 알림이 오거나 사용자가 새로고침을 누를 때까지 그 상태가 유지된다.

### 수정

데이터 소스를 현세대 알림 집계로 교체.

```diff
-import com.example.mycard.sms.SMSReader
+import com.example.mycard.notif.readNotifCardGroups

-            // SMS 읽기
-            val groups = SMSReader.readCardApprovalGrouped(context)
+            // 알림 기반 집계 읽기
+            val groups = readNotifCardGroups(context)
```

`readNotifCardGroups`는 `SMSReader.SmsGroup`을 그대로 반환하므로 Worker의 나머지 코드
(합계 계산, `widget_groups` JSON 조립, 텍스트 파일 저장)는 손대지 않았다. 항목의 `date`도
동일한 `yyyy-MM-dd HH:mm` 형식이라 `todayCount` 계산도 그대로 동작한다.

이로써 `SMSReader.readCardApprovalGrouped()`의 호출처는 하나도 남지 않았다. `SMSReader`는
이제 `SmsGroup` / `SmsItem` DTO 제공용으로만 쓰인다 (제거는 별건 작업).

## ② `Whitelist.DEFAULT_PACKAGES`에 롯데카드 패키지 누락

### 경위

`config/card_filters.json`에는 롯데카드 필터 2개(`lottecard_check_approval_v1`,
`lottecard_check_cancel_v1`)가 있고 패키지는 `com.lcacApp`이다. 그런데
`notif/Whitelist.kt`의 기본 10개 목록에 이 패키지가 빠져 있었다.

`CardNotificationListener`는 화이트리스트에 없는 패키지의 알림을 **파싱 시도조차 하지 않는다**
(`not whitelisted pkg=..., skipping parse`). 따라서 새로 설치한 단말에서는 롯데카드 필터가
영원히 동작하지 않는다.

현재 개발 단말에서 문제가 드러나지 않은 이유는 `/sdcard/Documents/MyCard/whitelist.txt`에
손으로 추가해 뒀기 때문이다. `Whitelist.ensureLoaded()`는 파일이 존재하면 그 내용만 쓰고
`DEFAULT_PACKAGES`를 아예 참조하지 않는다 (`docs/card-total-mismatch-2026-07-29.md`에
"whitelist.txt에 카드사 패키지 11개 모두 포함"으로 기록된 것이 이 상태다).

### 수정

```diff
         "com.example.mycard",
-        "com.shcard.smartpay"
+        "com.shcard.smartpay",
+        "com.lcacApp"
     )
```

**주의**: 이 수정은 `whitelist.txt`가 아직 없는 단말(신규 설치)에만 영향을 준다. 파일이 이미
있는 단말은 파일 쪽이 우선이므로 동작 변화가 없다.

## ③ `RawDump`가 알림 1건마다 파일 전체를 재작성하던 문제

### 경위

`RawDump.appendObject()`가 `ensureLoadedLocked()` → `cache.add()` → `writeAllLocked()`
순서로 동작했다. `writeAllLocked()`는 캐시의 모든 객체를 pretty-print로 재직렬화해
`raw_notifications_<year>.jsonl`을 **처음부터 다시 쓴다**.

파일이 연 단위이므로 12월에 가까워질수록 알림 1건당 수천 개 객체를 다시 쓰게 된다. 게다가
첫 알림에서 `ensureLoadedLocked()`가 파일 전체를 메모리로 올린다. 알림 수신 경로에서 발생하는
동기 I/O라 카드사 알림이 몰릴 때 리스너 콜백이 지연될 수 있다.

### 수정

append는 파일 끝에 한 건만 이어 쓰도록 분리했다. 전체 재작성은 실제로 필요한 경로
(`removeByTs` / `removeLineById` / `removeLinesByPkg` / `updateByTs`)에만 남긴다.

```diff
 fun appendObject(context: Context, obj: JsonObject) {
     synchronized(lock) {
-        ensureLoadedLocked()
-        cache.add(obj)
-        Log.d(TAG, "appendObject: cache size=${cache.size}")
-        writeAllLocked()
+        if (loaded) cache.add(obj)
+        appendToFileLocked(obj)
+        Log.d(TAG, "appendObject: loaded=$loaded cache size=${cache.size}")
     }
 }
```

`appendToFileLocked()`는 `RawDumpAll.appendObject()`와 동일하게
`FileOutputStream(f, append = true)`로 pretty-print JSON 한 건 + 개행을 이어 붙인다.

캐시 정합성:
- **이미 로드된 상태**면 캐시에도 추가하므로 `readAllObjects()` 결과가 파일과 일치한다.
- **아직 로드 전**이면 캐시를 건드리지 않고 파일에만 쓴다. 이후 누가 `readAllObjects()` /
  `removeByTs()` 등을 호출하면 그때 `ensureLoadedLocked()`가 파일을 읽으면서 방금 append한
  객체까지 함께 올라온다. 즉 알림 수신만 하는 동안에는 파일을 메모리로 올리지 않는다.

**파일 포맷은 그대로다.** 여전히 multi-line pretty JSON 객체가 개행으로 구분된 형태이므로
`docs/data/*.jsonl` 아카이브 절차(brace-balanced 스캐닝)나 `JsonStreamParser` 기반 읽기는
변경이 필요 없다.

## 검증

- `./gradlew assembleDebug` — BUILD SUCCESSFUL. 신규 경고 없음
  (`RawDump.kt:33` `Bundle.get` deprecation은 기존 것).
- 기존 `raw_notifications_2026.jsonl`은 손대지 않으며, append 결과 포맷이 동일함을 코드 경로로 확인.

## 단말 확인 항목

1. 홈 위젯에 이번 달 합계가 표시되는지 (설치 직후)
2. 다음날 01:00 이후 위젯 합계가 0으로 리셋되지 않는지 — ①의 실제 확인 항목
3. 카드 결제 알림 수신 시 `raw_notifications_2026.jsonl` 끝에 객체가 정상 추가되는지
4. ⋮ → 업데이트(rebuildFromRaw) 후 재구성 건수가 이전과 동일한지 (파일 포맷 회귀 확인)
5. 알림 기반 보기에서 항목 수정/삭제가 여전히 동작하는지 (전체 재작성 경로)
