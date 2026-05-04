# SK 현대카드 M 파싱 룰 추가 (2026-05-03)

## 대상 알림

- 패키지: `com.hyundaicard.appcard`
- 카드: SK 현대카드 M
- 샘플 ts: `1777795624693` (2026-05-03 17:07:04 KST)

## 알림 구조

```
title  : 현대카드
bigText: 홍길동 님, 현대카드 M 승인 17,400원 일시불, 5/3 17:07 
         한화커넥트 
         누적1,011,037원
```

## 추가된 필터

### `hyundaicard_sk_m_approval_v1` (type: approval)

```json
{
  "title_regex": "현대카드",
  "body_regex": "현대카드 M 승인\\s*(?<amount>[0-9,]+)원[\\s\\S]*?\\r?\\n(?<merchant>[^\\r\\n]+?)\\s*\\r?\\n누적"
}
```

## 검증

| 케이스 | 결과 |
|---|---|
| `현대카드 M 승인 17,400원 ... 한화커넥트 ... 누적` | ✅ 매칭, amount=17400, merchant=한화커넥트 |
| `네이버 현대카드 승인 12,900원 ...` (기존 네이버 카드) | ✅ 비매칭 |

## 비고

- 기존 `hyundaicard_naver_approval_v1`과 같은 패키지. 필터 순서상 네이버 현대카드가 먼저 평가됨 → "네이버 현대카드"가 "현대카드 M" 필터에 걸릴 일 없음 (body_regex에 "M 승인" 명시).

## Raw 데이터 아카이브

`docs/data/현대카드.jsonl` — com.hyundaicard.appcard 신규 8건 추가
