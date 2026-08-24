# Changelog

## [1.4.0.0] - 2026-08-24

카드사 앱의 8월 실적과 MyCard 집계를 카드 5장 전건 대조하고, 거기서 드러난 파싱 누락과
전체 소스 파악 중 발견한 구조적 결함을 함께 수정.

### Added
- **현대카드 알림 포맷 변경 대응 필터 2개** (`hyundaicard_naver_approval_v2`,
  `hyundaicard_sktm_approval_v2`) — 2026-08-20 알림부터 카드명 어순이
  `네이버 현대카드 승인` → `현대 네이버 승인`, `SKT M 통신할인2.0 승인` → `현대 SKT M할인형 승인`
  으로 바뀌어 8/20 이후 결제가 통째로 집계에서 빠지고 있었다.
  **기존 필터는 수정하지 않고 새 필터를 뒤에 덧붙였다.** `CardParser`가 파일 순서대로 첫 매칭을
  반환하므로 구 포맷은 기존 v1이, 신 포맷은 새 v2가 받는다. 포맷이 또 바뀌어도 v3를 덧붙이면
  과거 이력이 깨지지 않는다.
  카드명 전체를 리터럴로 박지 않고 브랜드 키워드(`네이버`/`SKT`)만 고정하고 `승인`까지를
  가변 구간으로 열어 어순 변화에 견디게 했다. 오탐은 뒤쪽 `
(가맹점)
누적` 앵커가 막는다.
- **SKT 통신요금 자동납부 필터** (`hyundaicard_autopay_skt_approval_v1`) — 기존 autopay 필터가
  merchant를 `SK브로드밴드` 리터럴로 고정해 둬서 SKT 통신요금이 5월부터 한 번도 파싱되지
  않고 있었다(매달 39,650원). 두 자동납부는 소속 카드가 달라(SK브로드밴드→네이버 현대,
  SKT→SK 현대) 별도 필터로 뒀다.
- 8월 대조 문서 및 현대카드 룰 문서, raw 알림 아카이브 33건.

### Fixed
- **`CardRefreshWorker`가 매일 01:00에 위젯 합계를 0으로 덮어쓰던 문제** — Worker가 구세대
  `SMSReader.readCardApprovalGrouped()`를 호출하고 있었다. 이 경로는 `cardGroup` prefs를 읽는데
  알림 기반 사용자는 이 값이 비어 있어 그룹 0개 → `widget_total = 0`이 기록됐다.
  `readNotifCardGroups()`로 교체.
- **`Whitelist.DEFAULT_PACKAGES`에 롯데카드 패키지 누락** — `com.lcacApp`이 빠져 있어 신규 설치
  단말에서는 롯데카드 필터 2개가 영원히 동작하지 않았다. (기존 단말은 `whitelist.txt`가
  우선하므로 영향 없음.)
- **한도 여유분 표시** — `잔여 71,960원`(기본색) → `-71,960원`(파랑). 초과 시 `⚠ +N원`(빨강)과
  부호·색이 대칭이 되게 했다. 메인/알림 기반 보기 양쪽에 공통 적용.

### Changed
- **`RawDump.appendObject`가 알림 1건마다 파일 전체를 재작성하던 것을 증분 append로 변경.**
  연 단위 파일이라 12월에 가까울수록 알림 1건당 수천 개 객체를 pretty-print로 다시 쓰고 있었다.
  전체 재작성은 실제로 필요한 경로(`removeByTs`/`updateByTs` 등)에만 남겼다.
  아직 로드 전이면 캐시를 건드리지 않고 파일에만 써서, 알림 수신만 하는 동안에는 파일을
  메모리로 올리지 않는다. **파일 포맷은 그대로**라 기존 아카이브 절차는 변경 불필요.

### 대조 결과 (2026-08-24 기준)
| 카드 | 카드사 앱 | 수정 전 | 수정 후 |
|---|---:|---:|---:|
| 신한 이마트 8423 | 221,360 (10건) | 221,360 | 221,360 |
| 하나 SK매직 9207 | 328,040 (12건) | 328,040 | 328,040 |
| 하나 SK브로드밴드 0179 | 305,780 (17건) | 305,780 | 305,780 |
| SK 현대카드 | 599,230 (15건) | 392,990 | **599,230** |
| 네이버 현대카드 | 188,430 (13건) | 131,730 | 247,140 |

네이버 현대카드 잔여 +58,710원은 원인이 특정돼 있다 — +30,000은 카드사 앱 미반영분(MyCard가
정확), +28,710은 현대카드 cancel 룰 부재. 취소 알림에 금액이 없어 룰을 만들지 못했다(미해결).

## [1.3.0.0] - 2026-08-18

`D:\workspace\sharecode`(com.cardtracker.app)에서 쓸 만한 기능을 골라 이식하고, 그 과정에서 발견한
레이아웃/상태 유지 문제를 함께 수정.

### Added
- **카드별 월 한도와 초과 경고** — 그룹(카드) 단위로 월 한도를 정하면 초과 시 헤더가 붉게 바뀌고
  `⚠ +28,040원`을 표시한다. 여유가 있으면 `잔여 N원`. 메인과 알림 기반 보기 양쪽에서 설정 가능
  (메인 ⋮ → "카드 한도", 알림 기반 보기 ⚙).
  저장은 `mycard_prefs`의 `card_limits`(JSON) — 위젯이 직접 읽을 수 있는 위치이고,
  직전에 올린 DB version 3 위에 마이그레이션을 더 얹지 않아도 된다.
- **카드 아바타** (`ui/CardBrand.kt`) — 실제 카드 비율의 미니 카드에 브랜드 그라데이션, IC 칩, 광택.
  라벨은 뒷 4자리 우선(`9207` `0179` `8423`), 없으면 `네이버` `SK` `현백` 등. MyCard의 그룹은
  카드사가 아니라 카드 한 장 단위이므로 첫 글자보다 뒷 4자리가 식별에 유용하다.
- **알림 접근 권한 경고** (`notif/ListenerAccess.kt`) — 이 앱의 집계는 알림 접근 권한 하나에 달려
  있는데, 꺼지면 에러 없이 조용히 멈추고 화면에는 예전 합계가 남아 "금액이 안 맞는" 것처럼 보인다.
  알림 기반 보기에는 배너, 메인에는 스낵바로 알리고 `ON_RESUME`에 재확인한다.
- **메인 화면 월별 보기** — 상단바에 `‹ 2026년 8월 ›`. 알림 기반 보기에만 있던 기능을 메인에도 넣었다.
- 한도 초과 요약 배너(알림 기반 보기), 취소 행 배경 강조(양쪽).

### Fixed
- **회전 시 펼친 카드가 초기화되던 문제** — 펼침 상태가 `remember`라서 구성 변경마다 날아갔다.
  `rememberSaveable` + `listSaver`로 교체(Set은 Bundle에 직접 저장되지 않음).
- **그룹 헤더가 3줄 이상으로 늘어나던 문제** — 글꼴이 큰 단말에서 이름·건수·한도가 각각 줄바꿈되어
  최대 6줄까지 늘어났다. 1행(아이콘·카드명·금액) / 2행(건수·한도) 2행 구조로 재구성하고 모든 Text를
  `maxLines = 1`로 묶었다.
- **한도 초과 색이 보이지 않던 문제** — 헤더 `Row`의 `secondaryContainer` 배경이 Card의
  `errorContainer`를 덮고 있었다. 헤더 배경 자체를 조건부로 전환.
- `readNotifCardGroups`가 상한 없이 `ts >= 이번 달 1일`로 조회해 미래 ts 알림이 섞일 수 있었다.
  `getParsedInRange(since, until)`로 월 구간을 닫았다.

### Changed
- `NotificationDao.getParsedSince` → `getParsedInRange(sinceTs, untilTs)`.
- `readNotifCardGroups(context, monthOffset = 0)` — 월 선택 지원.
- 지난 달을 보는 중에는 위젯을 갱신하지 않는다. 위젯은 항상 이번 달만 표시해야 한다.
- `isListenerPermissionGranted`를 `notif/ListenerAccess.kt`로 공용화하고
  `NotificationListActivity`의 중복 선언을 제거.

### Notes
sharecode에서 **가져오지 않은** 것과 이유:
- dedupe 키 `(issuer, amount, date, merchant)` — 같은 날 같은 가맹점 동일 금액 2회 결제를 삼킨다.
  v1.2.1.0에서 제거한 문제와 같은 계열.
- 취소 합산 — sharecode는 취소를 차감이 아니라 무시한다(승인 48,000 + 취소 -48,000 → 48,000).
  MyCard의 음수 저장 방식이 맞다.
- 카드사 이름 목록 기반 파서 — 현대카드 3장이 한 덩어리가 된다. MyCard가 카드 단위로 더 정밀하다.
- SMS 인박스 전체 재스캔, MM/DD 연도 추론 — 알림 ts를 쓰므로 불필요.


## [1.2.1.0] - 2026-07-29

7월 카드 합계가 카드사 앱과 어긋나는 원인을 전수 조사하고(`docs/card-total-mismatch-2026-07-29.md`) 확인된 4가지를 수정.

### Fixed
- **알림 유실** — `notifications` 테이블의 `(pkg,title,text,bigText)` UNIQUE 인덱스를 일반 인덱스로 교체.
  본문에 날짜가 없어 매달 문구가 동일한 자동납부 알림(예: `자동납부 승인 SK브로드밴드 21,890원`)이
  첫 달 이후 전부 `insert` 단계에서 버려지고 있었다. 대신 `insertIfNotRecentDuplicate(±5초)`로
  카드사 앱의 수 ms 간격 중복 발송만 걸러낸다. DB version 2→3, **기존 이력 보존 마이그레이션 포함**.
- `hyundaicard_sktm_ed_approval_v1` — 현대카드가 7/27부터 카드명 표기를 `SKT-M Ed3(통할2.0)` →
  `SKT M 통신할인2.0`으로 변경해 매칭이 끊긴 문제. `SKT-M` → `SKT[-\s]?M`으로 완화해 신·구 포맷 모두 수용.

### Added
- `hanacard_magic_9207_cancel_v1`, `hanacard_broad_0179_cancel_v1` — 하나카드 `(결제취소) -N원` 포맷.
  기존에는 하나카드 취소 룰이 아예 없어 승인만 잡히고 취소가 차감되지 않았다.
- `hanacard_sales_cancel_v1` — 하나카드 `매출취소 안내` 포맷. 이 알림에는 카드 식별자(`9*0*`/`0*7*`)가
  없어 2장 중 어디인지 특정할 수 없으므로, 오귀속을 피하려고 `하나카드 매출취소` **별도 그룹**으로 집계한다.
- `hanacard_magic_9207_overseas_approval_v1`, `hanacard_broad_0179_overseas_approval_v1` —
  하나카드 해외승인(`(0*7*)...KRW N/가맹점`) 포맷.

### Notes
- SKT 자동납부(`#SKT 07월-**23-2287`) 룰은 의도적으로 추가하지 않았다. 향후 해당 결제가 빠질 예정.
- 이미 유실된 6·7월 자동납부 건은 `raw_notifications_<year>.jsonl`에도 기록되지 않아
  `rebuildFromRaw`로 복구되지 않는다. 원본은 `raw_notifications_all_*.jsonl`에 남아 있다.

## [1.2.0.0] - 2026-05-31

### Added
- 알림 로그 및 알림 기반 카드 보기 월별 필터 추가 (PR #15)
- 현대백화점카드 필터 추가 (PR #14)

### Changed
- `hyundaicard_autopay_approval_v1` 필터 수정 — 자동납부 매칭 범위 조정 (PR #14)

## [1.1.0.0] - 2026-05-30

### Added
- `config/card_filters.json` git 관리 시작 — 필터 소스를 임시 파일 대신 버전 관리하에 보관
- `hyundaicard_sktm_ed_approval_v1` 필터 추가 — "SKT-M EdX(통신할인형)" 카드명 포맷 대응
- CLAUDE.md: Room DB 직접 수정 금지 규칙 추가 (Windows 파이프 개행 변환으로 인한 DB 손상 방지)
- CLAUDE.md: card_filters.json 소스 관리 절차 추가 (config/ 편집 → 검증 → commit → 배포)

### Changed
- `hyundaicard_autopay_approval_v1` 필터 수정 — SK브로드밴드 자동납부만 매칭하도록 한정 (SKT 자동납부 제외)
