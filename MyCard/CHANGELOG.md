# Changelog

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
