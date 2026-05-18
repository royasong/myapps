# Raw Notification 파일 월/연별 파티셔닝 (2026-05-18)

## 배경

`raw_notifications_all.jsonl`이 32MB에 도달하며 월 ~32MB 속도로 증가 중.
단일 파일 유지 시 1년 후 수백MB가 되어 `adb pull` / 파싱하자 워크플로우에 지장이 생길 것으로 판단.

## 변경 내용

### 파일별 파티션 전략

| 파일 | 이전 이름 | 새 이름 패턴 | 주기 |
|---|---|---|---|
| 전체 알림 dump | `raw_notifications_all.jsonl` | `raw_notifications_all_YYYY_MM.jsonl` | 월단위 |
| whitelist 알림 | `raw_notifications.jsonl` | `raw_notifications_YYYY.jsonl` | 연단위 |

### 수정 파일

- `app/src/main/java/com/example/mycard/notif/RawDumpAll.kt`
  - `FILE_NAME` 상수 제거
  - `currentMonthFilename()` 추가: `"raw_notifications_all_${year}_${month_02d}.jsonl"`
  - `file()` → `AppStorage.file(currentMonthFilename())`

- `app/src/main/java/com/example/mycard/notif/RawDump.kt`
  - `FILE_NAME` 상수 제거
  - `loadedFilename: String?` 필드 추가 (로드 시점 파일명 저장 — write가 항상 같은 파일로 가도록)
  - `currentYearFilename()` 추가: `"raw_notifications_${year}.jsonl"`
  - `ensureLoadedLocked()`: 로드 전 `loadedFilename = currentYearFilename()` 설정
  - `writeAllLocked()`: `AppStorage.file(loadedFilename ?: currentYearFilename())` 사용
  - `invalidate()`: `loadedFilename = null` 추가

## 전환 시나리오

### 큰 파일 전환
- 오늘(2026-05-18)부터 `raw_notifications_all_2026_05.jsonl`에 기록 시작
- 구 `raw_notifications_all.jsonl`은 읽기 전용 — 앱이 더 이상 쓰지 않음
- **2026-06-01 이후**: 구 `raw_notifications_all.jsonl` 수동 삭제

### 작은 파일 전환
- `adb shell mv raw_notifications.jsonl raw_notifications_2026.jsonl`로 즉시 rename
- 기존 140KB 데이터 보존, 앱이 `currentYearFilename()`으로 `raw_notifications_2026.jsonl`을 읽고 씀
- 구 `raw_notifications.jsonl` 파일 없음 (rename되었으므로)

## 설계 결정 사항

**`loadedFilename` 필드를 둔 이유:**  
`RawDump`는 in-memory 캐시 singleton. `writeAllLocked()` 시점에 연도가 바뀌어 있으면 캐시(구 연도 데이터)가 새 연도 파일에 잘못 기록될 수 있다. 로드 시점의 파일명을 고정하면 write는 항상 같은 파일로 가고, `invalidate()` + 다음 로드 시 자연스럽게 새 연도 파일로 전환된다.

**연말 자정 edge case:**  
앱 프로세스가 살아있는 채로 연도가 바뀌면 `loaded=true`이고 `loadedFilename`이 구 연도 파일을 가리킴 → 앱 재시작 전까지 구 연도 파일에 계속 기록. 허용 가능한 tradeoff — 연말 자정 시나리오는 극히 드물고 앱 재시작 시 자동 해결됨.

## 검증

설치 후 즉시 `raw_notifications_all_2026_05.jsonl` (20KB) 생성 확인:
```
-rw-rw---- 1 u0_a305 media_rw 20729 2026-05-18 21:30 raw_notifications_all_2026_05.jsonl
```
