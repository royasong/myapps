# 네이버 릴레이 카드 SMS 파싱 지원 (2026-04-28)

## 배경

사용자가 현대카드 승인 SMS 한 건이 합산에 포함되지 않는다고 보고하셨습니다.
받은 본문은 다음과 같습니다.

```
네이버 현대카드 승인
차*욱
11,000원 일시불
04/28 13:03
서울전통육개장판교
누적 901,347원
```

## 원인 분석

`MyCard/app/src/main/java/com/example/mycard/sms/SMSReader.java` 의
`readCardApprovalGrouped(...)` 안에서 SMS Inbox 를 다음 SQL 조건으로 조회하고 있었습니다.

```java
String selection = "address = ? AND date >= ? AND body LIKE '[Web발신]%'";
```

즉 본문이 `[Web발신]`으로 시작하는 메시지만 가져오는 구조라,
`네이버 현대카드 승인 ...` 으로 시작하는 메시지는 **DB 쿼리 단계에서 이미 제외**되어
이후의 금액 파싱(`AMOUNT_PATTERN`) 로직까지 도달하지 못했습니다.

본문 정규화 라인도 `[Web발신]` prefix만 벗겨내도록 작성되어 있었습니다.

```java
String trimmedBody = body
        .replaceFirst("^\\[Web발신\\]\\s*", "")
        .replaceAll("\\s+", "");
```

## 수정 내용

옵션 B: **[Web발신] / 네이버 두 prefix 를 OR 로 모두 허용**.

### 1) SQL 조건 완화 (`SMSReader.java:136`)

```java
String selection = "address = ? AND date >= ? AND (body LIKE '[Web발신]%' OR body LIKE '네이버 %')";
```

`[Web발신]%` 혹은 `네이버 %` 로 시작하는 메시지 모두 조회 대상이 됩니다.
`네이버 ` 뒤에 공백 한 칸을 강제해 두어, 무관한 네이버 프로모션 SMS 가
대량으로 끌려오는 것을 어느 정도 줄였습니다.

### 2) 본문 prefix 정규화 확장 (`SMSReader.java:164`)

```java
String trimmedBody = body
        .replaceFirst("^\\[Web발신\\]\\s*", "")
        .replaceFirst("^네이버\\s+", "")
        .replaceAll("\\s+", "");
```

`[Web발신]` 과 `네이버 ` prefix 둘 다 제거합니다 (양립 불가능하므로 적용 순서는 무관).
이후 카드사명(예: `현대카드`)이 본문 첫머리에 노출되어 `configId` 매칭이 자연스럽게 통과합니다.

## 영향 범위

- 기존 `[Web발신]` 형식은 정규식이 이전과 동일하게 동작 → 회귀 없음.
- `자동결제` → `승인` 치환, 취소 패턴(`CANCEL_AMOUNT_PATTERN`),
  `MASKED_LAST4` 카드 끝자리 검증 등 이후 로직은 그대로 사용.
- RCS (`addRcsApprovals`) 경로는 별도 흐름이므로 이번 변경의 영향 없음.

## 검증된 케이스

위에 인용한 본문을 기준으로 정규화 결과를 손으로 추적해 보면,

```
네이버현대카드승인차*욱11,000원일시불04/2813:03서울전통육개장판교누적901,347원
```

`AMOUNT_PATTERN = 승인[^\d]*(\d[\d,]*)원` 이 `승인` → `차*욱` (비-digit 시퀀스) → `11,000원` 으로 매칭되어
**11,000 원**이 정상 추출됩니다.

## 빌드 / 설치

```bash
cd MyCard
./gradlew installDebug
```

`installDebug` 는 `uninstall` 을 거치지 않고 APK 만 덮어쓰기 때문에
앱 SharedPreferences (`mycard_prefs`) 의 카드 그룹 / 누적 위젯 상태는 보존됩니다.

연결된 기기 (`SM-F741N - 16`) 에 설치 완료.

## 후속 점검 포인트

1. 사용자의 `cardGroup` 설정에서 `현대카드` ID 가 정확히 등록되어 있는지 확인.
   (네이버 릴레이 본문에도 `현대카드` 토큰이 그대로 들어있어 `contains()` 매칭은 통과.)
2. 다른 카드사도 네이버 릴레이로 들어오는 경우(`네이버 신한카드`, `네이버 삼성카드` 등)가 있는지 확인.
   같은 prefix 패턴이라면 추가 작업 없이 그대로 동작.
3. `네이버 ` 로 시작하지만 카드 승인이 아닌 SMS (예: 네이버페이 등) 는
   `승인`/`취소`/금액 어느 패턴에도 걸리지 않으면 라인 178 의 `if (!hasApproval && !hasCancel && !hasAmount) continue;` 에서 컷됩니다.
   다만 false positive 가 발견되면 SQL 단의 `네이버 %` 조건을 보다 좁힐 필요가 있습니다.

---

## 추가 보고: RCS 단일 TextView 본문이 누락되던 문제

위 SMS 경로 수정 후에도 사용자가 "리스트에 안 보인다"고 보고하셨습니다. 단말의
실제 데이터를 직접 조회해보니 (`adb shell content query --uri content://im/chat
--where "address='15776200'"`) 해당 메시지는 **SMS Inbox 에는 없고 RCS
저장소 (`content://im/chat`) 에 `application/vnd.gsma.openrichcard.v1.0+json`
content_type 으로 들어와** 있었습니다.

### RCS body 의 layout 구조

```json
{
  "messageHeader": "[Web발신]",
  "card": "open_rich_card",
  "layout": {
    "widget": "LinearLayout",
    "children": [
      ...
      {
        "widget": "TextView",
        "text": "네이버 현대카드 승인\n차*욱\n11,000원 일시불\n04/28 13:03\n서울전통육개장판교\n누적 901,347원"
      }
    ]
  }
}
```

기존 `addRcsApprovals(...)` 는 RCS 카드에 `금액`, `사용처`, `거래시간`, `카드`,
`거래구분`, `누적금액` 같은 **라벨이 별도 TextView 로 분리**되어 있다고 가정하고
`findAmountAfterLabel(texts, "금액")` 으로 금액을 추출했습니다.

위 구조처럼 **TextView 가 단 하나**이고 그 안에 SMS 원문이 통째로 들어오는
"네이버 릴레이 형 RCS" 카드는 라벨이 존재하지 않아 `labelAmount == null`
→ `continue` 로 건너뛰어 결과 리스트에서 누락되었습니다.

### 추가 수정 (`SMSReader.java:259~`)

`addRcsApprovals` 안에 fallback 경로를 추가했습니다.

1. 카드 끝 4자리 (`cardLast4`) 매칭이 라벨 기반으로 실패하면 `concat`(전체 텍스트
   결합 + whitespace 제거) 에 대해 다시 시도.
2. `findAmountAfterLabel` 이 `null` 을 반환하면, `concat` 에 대해 SMS 경로와
   동일한 방식 — `자동결제` → `승인` 치환 후 `CANCEL_AMOUNT_PATTERN` /
   `AMOUNT_PATTERN` 적용 — 으로 금액을 재추출.
3. 표시 본문(`displayBody`)은 라벨 기반 경로 성공 시 기존 조립식, 실패 시
   `[Web발신] {첫 TextView 원문}` 으로 fallback (개행 보존).

승인/취소 부호 처리도 SMS 와 일관되게 적용 (`isCancel ? -amount : amount`).
기존 RCS 라벨 분리형 카드(주력 케이스)는 분기 첫 번째 경로로 그대로 동작하므로
회귀 없음.

### 검증 흐름

`adb logcat -s SACH` 에서 다음 두 로그 라인을 확인하면 OK 입니다.

- `RCS approval: key=현대카드 amount=11000 merchant= time=` — fallback 경로(라벨이
  없으므로 merchant/time 은 빈 문자열).
- 합산: 누적 위젯 데이터(`widget_total`)에 11,000 원이 더해진 값이 반영.

### 빌드 / 설치

```bash
cd MyCard
./gradlew installDebug
```

데이터 보존 상태로 `SM-F741N` 단말에 재설치 완료.
