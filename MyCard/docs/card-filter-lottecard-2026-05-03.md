# 롯데카드 파싱 룰 추가 (2026-05-03)

## 대상 알림

- 패키지: `com.lcacApp`
- 카드: 롯데카드 체크 (포인트+, 카드번호 `7*4*`)
- 샘플 ts: `1777802051623` (2026-05-03 18:54:11 KST)

## 알림 구조

```
title  : 롯데마트 롯데몰 수지점         ← 가맹점명
bigText: 46,170원 승인
         포인트+(7*4*)
         일시불, 05/03 18:54
```

## 추가된 필터

### `lottecard_check_approval_v1` (type: approval)

```json
{
  "title_regex": "(?<merchant>.+)",
  "body_regex": "(?<amount>[\\d,]+)원\\s*승인"
}
```

### `lottecard_check_cancel_v1` (type: cancel)

```json
{
  "title_regex": "(?<merchant>.+)",
  "body_regex": "(?<amount>[\\d,]+)원\\s*취소"
}
```

취소 알림 샘플 미확인 — 취소 발생 시 실제 알림으로 검증 필요.

## 광고 비매칭 검증

| 알림 | 결과 |
|---|---|
| title="차상욱님이 어제 적립한 L.POINT는?" / body="2포인트 모았어요." | ✅ 비매칭 (body에 "승인" 없음) |

## Raw 데이터 아카이브

`docs/data/롯데카드.jsonl` — com.lcacApp 고유 알림 4건 저장
