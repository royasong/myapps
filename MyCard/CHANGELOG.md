# Changelog

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
