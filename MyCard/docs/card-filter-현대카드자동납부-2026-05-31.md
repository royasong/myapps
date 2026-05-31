# 현대카드 자동납부 필터 추가 및 수정 (2026-05-31)

## 대상 알림

- 패키지: `com.hyundaicard.appcard`
- 카드명: 네이버 현대카드
- 대표 ts: 1779514200600 (SK브로드밴드, 2026-05-22), 1778564473424 (SKT, 2026-05-12)

## 알림 구조

```
title  : 현대카드
text   : 자동납부 승인 차*욱님 SK브로드밴드 21,890원
bigText: 자동납부 승인 차*욱님 SK브로드밴드 21,890원

title  : 현대카드
text   : 자동납부 승인 차*욱님 SKT 05월-**23-2287 39,650원
bigText: 자동납부 승인 차*욱님 SKT 05월-**23-2287 39,650원
```

## 필터 정보

```json
{
  "id": "hyundaicard_autopay_approval_v1",
  "card_company": "네이버 현대카드",
  "package": "com.hyundaicard.appcard",
  "match": {
    "title_regex": "현대카드",
    "body_regex": "자동납부 승인\\s+\\S+\\s+(?<merchant>SK브로드밴드)\\s+(?<amount>[0-9,]+)원",
    "type": "approval"
  }
}
```

## 검증 표

| 케이스 | 입력 | merchant | amount | 결과 |
|---|---|---|---|---|
| SK브로드밴드 (매칭) | `자동납부 승인 차*욱님 SK브로드밴드 21,890원` | SK브로드밴드 | 21890 | ✓ |
| SKT (비매칭) | `자동납부 승인 차*욱님 SKT 05월-**23-2287 39,650원` | — | — | ✓ |

## Raw 데이터 아카이브

`docs/data/현대카드.jsonl` 에 2건 추가 (SKT 비매칭 포함, negative 검증 자료).

## 수정 이력

| 날짜 | 변경 내용 |
|---|---|
| 2026-05-30 | 필터 최초 추가. body_regex: `(?<merchant>\S+).*` — SKT/SK브로드밴드 모두 매칭 |
| 2026-05-31 | SKT 자동납부는 DB 제외 대상임을 확인. body_regex를 `(?<merchant>SK브로드밴드)` 고정으로 수정 |
