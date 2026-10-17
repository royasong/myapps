# CLAUDE.md 현행화 (2026-10-17)

## 배경

Fold7(`R3CY70RDW3B`)에서 메인 리스트가 비어 보이는 문제가 있었다. 로그 확인 결과
`CardFilterStore: readShared: path=/storage/emulated/0/Documents/MyCard/card_filters.json exists=false`
— 단말에 필터 파일이 없어 모든 알림이 파싱 실패하고 있었다.

`config/card_filters.json`을 `adb push`로 올리고 ⋮ → 업데이트(rebuildFromRaw) 후 리스트 정상 표시 확인.
CLAUDE.md의 배포 절차(MediaStore owner 유지 / `content write`)는 현재 코드와 맞지 않았다.
`CardFilterStore`는 이제 `AppStorage`(`MANAGE_EXTERNAL_STORAGE` + `java.io.File`)로 직접 읽기 때문이다.

## CLAUDE.md 변경 내용

| 항목 | 이전 | 현행 |
|---|---|---|
| 앱 개요 | SMS inbox 파싱, `READ_SMS` only | 알림 기반 (NotificationListener → card_filters.json 파싱 → Room) |
| 데이터 흐름 / 저장소 | 없음 | `CardNotificationListener` 흐름도, `/sdcard/Documents/MyCard/` 파일 목록 추가 |
| prefs 키 | `cardGroup`, `memo`, `widget_total`, `widget_groups` | `widget_today_count`, `widget_total_count`, `card_limits` 추가, `cardGroup`의 현재 용도 명시 |
| Components | SMSReader 중심 | notif/parser 패키지 중심, SMSReader/RcsParser는 미사용으로 표시 |
| CardRefreshWorker | "Not scheduled" | 매일 01:00 periodic enqueue |
| 권한 | READ_SMS only | 알림 접근 / 전체 파일 접근이 핵심 |
| `MainActivity.kt:208` `"l\`"` quirk | 존재 | 코드에 없어 삭제 |
| card_filters.json 배포 | MediaStore owner 유지 + `content write` | `adb push` + force-stop + ⋮ → 업데이트 |
| raw 파일명 | `raw_notifications_all.jsonl` | `raw_notifications_all_<YYYY>_<MM>.jsonl`, `raw_notifications_<YYYY>.jsonl` |
| 디버깅 단서 | `ensureFileUri: no row matched` | `readShared: exists=false` / `canRead=false` |

코드 변경은 없다.
