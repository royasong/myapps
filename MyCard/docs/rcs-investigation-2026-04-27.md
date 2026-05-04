# RCS(MaaP) 카드 승인 메시지 조사 및 대응 방향

작성일: 2026-04-27
조사 단말: 플립 (`R3CX705W62D`)
원본 덤프: `app/build/tmp/im_chat_18001111.txt` (62 rows, git-ignore)

---

## 1. 발견 (요약)

`SMSReader.java`가 못 잡는 카드 승인 메시지가 **별도의 RCS / Samsung MaaP 데이터베이스**에 저장돼 있습니다.

- 위치: `content://im/chat` (Samsung `com.samsung.android.messaging` 앱 소유)
- 제공자 클래스: `com.samsung.android.messaging.service.provider.MessageContentProvider`
- 본문은 plain text가 아닌 **카드 UI 레이아웃 JSON** (RCS Open Rich Card / GSMA MaaP)
- 이 단말의 18001111(하나카드) 메시지의 경우, 18001111로 온 RCS 카드 46건이 추가로 존재 → 일반 SMS 인박스 11건 외 별도

대상 케이스로 시작이 된 메시지: 959,100원 / 하나0\*7\* / 04/09 13:05 / (주)케이티알파 — 일반 SMS에는 0건, RCS에는 `_id=653`으로 존재.

## 2. 데이터 구조

### 2.1 record 컬럼 (`content://im/chat`)

핵심만 추리면:
- `_id`, `thread_id`
- `address` — 발신번호 (예: `18001111`, 하이픈/국가코드 없는 형태)
- `date`, `date_sent` — epoch millis
- `type`, `read`, `seen`, `status`
- `body` — **JSON 문자열** (또는 `text/plain`인 경우 일반 텍스트)
- `content_type` — 페이로드 종류 (아래)
- `recipients`, `service_type`, `sim_slot`, `sim_imsi`
- `creator`(=`com.samsung.android.messaging`), `is_bot`, `maap_traffic_type`(`advertisement` 등)

### 2.2 `content_type` 분포 (이 단말 18001111 기준 62건)

| content_type | 건수 | 의미 |
|---|---|---|
| `application/vnd.gsma.openrichcard.v1.0+json` | 46 | 카드 UI 레이아웃 (거래/안내) |
| `application/vnd.gsma.xbotmessage.v1.0+json` | 16 | 챗봇/광고 메시지 (`title`/`description`) |

전 발신번호에서 `openrichcard` 사용 분포:

| address | 건수 | (추정) |
|---|---|---|
| 18001111 | 46 | 하나카드 |
| 15447200 | 28 |  |
| 15889955 | 26 |  |
| 0269589000 | 26 |  |
| 15990501 | 13 |  |
| 15447000 | 2 |  |
| 0261216361 | 2 |  |
| 16617654 | 1 |  |
| 15888700 | 1 |  |
| 15888100 | 1 |  |
| 15776000 | 1 | 현대카드(SMS 변경 안내) |
| 0424842025 | 1 |  |

→ **여러 발신번호가 동일한 `openrichcard` 포맷을 사용**. 표준화돼 있어 한 파서로 다수 카드사 커버 가능.

### 2.3 `openrichcard` body JSON 스키마 (거래 케이스)

이 단말 18001111의 46개 openrichcard 중 **45개가 동일 거래 카드 스키마**입니다:

```json
{
  "messageHeader": "[Web발신]",
  "verifiedIndicator": "1",
  "messageHeaderExtension": "확인된 발신번호",
  "card": "open_rich_card",
  "layout": {
    "widget": "LinearLayout",
    "children": [
      { "widget": "ImageView", "mediaUrl": "https://image.skt-maap-api.com/.../o_3a0c....png", ... },
      { "widget": "LinearLayout",
        "children": [
          { "widget":"LinearLayout","children":[
              {"widget":"TextView","text":"금액"},
              {"widget":"TextView","text":"959,100원"}] },
          { "widget":"View" /* 구분선 */ },
          { "widget":"LinearLayout","children":[
              {"widget":"TextView","text":"카드"},
              {"widget":"TextView","text":"하나0*7*"}] },
          /* 손님명 / 거래종류 / 거래구분 / 사용처 / 거래시간 */
          { "widget":"View" },
          { "widget":"LinearLayout","children":[
              {"widget":"TextView","text":"누적금액"},
              {"widget":"TextView","text":"1,886,600원"}] }
        ]
      }
    ]
  },
  "suggestions": [ /* 이용내역확인 버튼 등 */ ]
}
```

**필드 8쌍이 모두 등장 (45/45)**: `금액`, `카드`, `손님명`, `거래종류`, `거래구분`, `사용처`, `거래시간`, `누적금액`. 누락 케이스 없음 → 안정적 파싱 가능.

> 나머지 1개 openrichcard는 `하나Pay 가입 안내` (단순 텍스트 알림). `mediaUrl`이 다른 이미지(`o_9e7...`)이고 layout에 한 덩어리 텍스트만 있어 거래 카드와 구별됨.

### 2.4 화면의 "승인" 라벨은 본문에 없다

캡처 상단의 ✓ + "승인" 뱃지는 **JSON 본문에 텍스트로 들어있지 않고**, `mediaUrl`이 가리키는 이미지로 렌더링됩니다. 따라서 기존 `SMSReader`의 `승인[^\d]*\d[\d,]*원` 정규식은 적용 불가.

### 2.5 "취소" RCS 카드는 없다 (이 단말 기준)

- im/chat에서 18001111로 검색 시 본문에 "취소"가 들어 있는 record: 1건. 그러나 이는 광고(xbotmessage) 본문 약관 텍스트의 단어로, 거래취소 카드가 아님.
- 18001111에서 받은 거래취소(`취소 X,XXX원`)는 **일반 SMS(`content://sms/inbox`)로만** 들어옴. 인박스 덤프에서 7건 확인됨 (4/11 13,900원, 4/7 18,000원, 1/30 17,260원, 12/21 10,000원, 11/17 187,350원, 7/19 100,000원, 4/25 16,424원 해외매출취소).

→ **RCS 카드 = 항상 승인** 으로 가정해도 (이 단말·이 발신번호 기준으로) 안전. 취소 건은 기존 SMS 경로가 그대로 처리.

### 2.6 `mediaUrl` 변형 (참고)

이 단말 18001111 기준:
- `o_3a0ca621fb1044308e9121d0.png` — 44건 (거래 카드 표준)
- `o_0c8688e398d64a188f3c17d9.png` — 1건 (`_id=306`, 187,910원, 스마일페이_옥션, 11/16 12:18, 누적 557,670원)
- `o_9e708f0dbfb54209ab28b335.png` — 1건 (하나Pay 가입 안내)

`o_0c...`는 거래시간/사용처 등이 있는 정상 거래 카드라 "취소"는 아님 (해외매출/온라인 변형 추정, 샘플 1건이라 단정 불가). 어떤 mediaUrl이 와도 layout 구조는 동일하므로 **이미지 분기 없이 layout text 페어로 파싱하면 충분**.

## 3. 영향: 현재 앱이 누락하는 데이터

`SMSReader.readCardApprovalGrouped()`는 `content://sms/inbox`만 읽음 → 이 단말의 경우 18001111의 **이번 달 큰 금액 결제(959,100원 포함)가 모두 누락**. 인박스에 남는 것은 소액/특정 가맹점/취소 위주.

→ 사용자가 보는 "이번 달 총 승인" 금액은 **실제보다 현저히 낮게 표시**됩니다. 위젯도 동일.

## 4. 권한 / 접근 가능성 (불확실)

`content://im/chat`은 adb shell로는 잘 조회되지만, 일반 3rd-party 앱이 `READ_SMS`만으로 접근 가능한지는 **단말/통신사/Samsung Messaging 빌드에 따라 다릅니다.** 알려진 사실:

- Samsung MessageContentProvider는 manifest에 `im` authority를 등록하지만, 어떤 빌드에서는 `signature` 보호 또는 `WRITE_SMS`/`READ_SMS` 요구.
- 가계부/카드 알림 앱들 다수가 `NotificationListenerService` 우회를 사용하는 이유 — 직접 접근 차단 케이스가 흔함.

→ **즉시 코드로 검증 필요** (probe). 결과에 따라 분기.

## 5. 진행 방향 결정

3-Phase 로 진행. 각 Phase의 결정 기준 명확히.

### Phase 0. Probe (먼저, 변경 최소)

목적: 우리 앱이 `READ_SMS`만으로 `content://im/chat`을 읽을 수 있는지 확인.

작업:
- `SMSReader`에 `probeRcsAccess(Context)` 정적 메서드 추가. `cr.query(Uri.parse("content://im/chat"), null, "address=?", new String[]{"18001111"}, "date DESC LIMIT 1")` 시도.
- 결과(성공/SecurityException/empty)와 `Cursor` row count를 `Log.i("SACH", ...)`로 기록.
- `MainActivity` 진입 시 1회 호출.

판정:
- **성공 + row >= 1** → Phase 1.
- **SecurityException** → Phase 2.
- **성공이지만 row 0** → 더 조사 필요 (필터·권한 부분차단 가능).

### Phase 1. RCS 직접 조회 (Probe 성공 시)

`SMSReader`에 RCS 경로 추가:

1. `readRcsApprovalsGrouped(context)`:
   - 같은 `cardGroup` 설정 (`phone,id`)을 재사용.
   - 쿼리: `content://im/chat`, `address = phone AND date >= startOfMonth AND content_type = 'application/vnd.gsma.openrichcard.v1.0+json'`.
   - 본문 JSON 파싱 — Gson(이미 의존 중)으로 디코드 후 layout tree를 깊이우선 순회하며 `widget==TextView`의 `text` 시퀀스를 평탄화.
   - 평탄화된 시퀀스에서 라벨/값 페어 매칭:
     - `금액` 다음 `text`에서 `(\d[\d,]*)원` 추출 → amount
     - `사용처` 다음 `text`로 가맹점명 (표시용)
     - `거래시간` 다음 `text`로 MM/dd HH:mm (표시·dedup용)
     - 옵션: `누적금액`, `카드`, `거래구분`
   - `id` 매칭은 기존 SMS와 동일하게 본문(평탄화 텍스트)에 `configId` 포함 여부.
   - **금액은 항상 양수**(승인). 취소 케이스는 RCS에 없음.
2. `readCardApprovalGrouped(context)` 결과와 머지:
   - 그룹 키 동일(`configId`). 두 List를 합쳐 `total = sum(amounts)` 재계산.
   - **중복 dedup**: 같은 결제가 SMS와 RCS 양쪽으로 들어오는지 확인. 들어온다면 `(거래시간, 금액, 사용처 4글자)` 합성키로 한쪽만 채택. 들어오지 않으면 dedup 불필요.
3. 위젯/UI는 변경 없음(같은 `List<SmsGroup>` 인터페이스 유지).

검증: 4월 데이터에서 RCS 합산 후 959,100원이 18001111 그룹 합계에 포함되는지, 사용자가 알고 있는 실 결제 합계와 일치하는지.

### Phase 2. NotificationListenerService 우회 (Probe 차단 시)

차단되면:

- `NotificationListenerService` 서브클래스 추가, manifest `BIND_NOTIFICATION_LISTENER_SERVICE`.
- 사용자가 한 번 `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`에서 권한 부여 (`SettingsActivity`에 진입 버튼 추가).
- `onNotificationPosted`에서 `pkg == com.samsung.android.messaging`, `extras.getCharSequence(EXTRA_TITLE)/EXTRA_TEXT/EXTRA_BIG_TEXT` 추출.
- 추출한 텍스트를 prefs에 누적 (별도 키, 예: `rcs_inbox` JSON 배열) → `SMSReader`의 그룹화 단계에서 함께 합산.
- 이 방법은 **알림이 도착한 시점**에만 캡처 가능하므로, 과거 분 누락. 초기 도입 시 사용자에게 "이번 달은 RCS 합산이 부족할 수 있음" 안내 필요.

### Phase 3. (선택) MaaP 본문에서 추가 분석

여유 있으면:
- `누적금액` 필드를 활용해 카드사가 산출한 누적과 우리 합산이 일치하는지 검증 → 누락/중복 디버깅 도구.
- `사용처` 별 그룹핑 추가 (현재는 `카드ID`만 그룹핑).

## 6. 진행 결과 (2026-04-27 당일)

### Phase 0 — Probe (완료, 성공)

`SMSReader.probeRcsAccess(Context)` 추가, `MainActivity.onCreate`에서 1회 호출. 플립(`R3CX705W62D`)에서 검증.

logcat 결과:
```
I SACH    : RCS probe begin: uri=content://im/chat, pkg=com.example.mycard
I SACH    : RCS probe: rowCount=685, colCount=4
I SACH    : RCS probe row[0]: _id=693 address=15776000 ... openrichcard
I SACH    : RCS probe row[1]: _id=691 address=01055232287 ... text/plain
I SACH    : RCS probe row[2]: _id=690 address=18001111 ... xbotmessage
```

→ `READ_SMS`만으로 `content://im/chat` 접근 가능. Phase 2(NotificationListener) 불필요.

### Phase 1 — RCS 직접 조회 (완료, 동작 확인)

`SMSReader`에 다음 추가:
- 상수 `RCS_OPENRICHCARD`, `RCS_URI`.
- `addRcsApprovals(cr, fmt, phone, configId, startTime, groupMap)` — 기존 SMS 루프 바로 뒤에 호출됨.
- 헬퍼: `extractRichCardTexts(body)`(Gson `JsonParser` + DFS), `findAmountAfterLabel`, `findValueAfterLabel`, `collectTextDfs`.

흐름:
1. `address = phone AND date >= startOfMonth AND content_type = 'application/vnd.gsma.openrichcard.v1.0+json'` 쿼리.
2. body JSON → `widget=="TextView"`인 모든 노드에서 `text` 값을 깊이우선 순서대로 수집.
3. 평탄화 텍스트를 모두 이어붙여 공백 제거 후 `configId`(예: `하나`) 포함 여부 검사 — 미포함 시 skip.
4. 라벨 `금액` 다음 텍스트에서 `(\d[\d,]*)원` 매치 → amount.
5. RCS는 항상 승인이므로 양수로 저장. **취소 분기 없음**.
6. 본문은 JSON을 그대로 두지 않고, 사람이 읽을 수 있는 한 줄 (`[Web발신] <카드> 승인 <거래구분> <금액>원 <거래시간> <사용처> 누적 <누적금액>`) 로 정제해 `SmsItem.body`에 저장 (Compose UI의 기존 텍스트 가공 로직과 호환).

검증 (logcat):
```
I SACH    : RCS approval: id=하나 amount=959100 merchant=(주)케이티알파 time=04/09 13:05
```

검증 (UI):
- 이번 달 총 승인: **973,300원** (= SMS 14,200 + RCS 959,100).
- `하나` 그룹 펼치면 RCS 항목 한 줄 표시: `하나0*7* 승인 일시불 959,100원 04/09 13:05 (주)케이티알파`. "승인" 라벨, 우측 금액 모두 정상.

### Phase 1.5 — `cardGroup`의 3번째 파라미터 = 카드 끝 4자리 (완료)

기존 `phone,id[,keyword]` 포맷의 3번째 토큰은 dead code(`findCardIdByPhone`)에서만 참조되던 "keyword". 이를 **카드번호 뒤 4자리**로 의미 변경해, 같은 발신번호에서 들어오는 여러 카드를 카드별로 필터링·분리할 수 있게 함.

매칭 방식: 본문/RCS `카드` 필드에서 4글자 마스킹 토큰(`[0-9*]{4}` 중 `*` 2개 이상)을 모두 찾고, 각각을 사용자가 입력한 4자리와 **자릿수 위치별로** 비교. `*` 위치는 wildcard로 통과.

| masked (본문) | last4 (입력) | match? |
|---|---|---|
| `0*7*` | `1111` | ✓ (pos 0=0, pos 2=7) |
| `0*7*` | `2222` | ✗ (pos 0 mismatch) |
| `9*0*` | `2222` | ✓ (pos 0=9, pos 2=0) |
| `**34` | `1234` | ✓ (pos 2=3, pos 3=4) |

`last4`가 비어 있으면 필터 미적용(하위호환). 매치 강화: 카드사 표준 마스킹은 2-2 비율이므로 별 2개 이상인 토큰만 후보로 채택 → 본문에 우연히 들어가는 `20*0` 같은 비-마스킹 시퀀스의 false positive 방지.

`SettingsActivity`의 placeholder 텍스트도 `예: 스타벅스/쿠팡/네이버` (의미 무관한 옛 더미)에서 실제 포맷 예시로 갱신.

검증 (사용자 설정: `15776000,현대` / `18001111,하나,2222` / `18001111,하나,1111`):
- `15776000,현대` → 0건 (15776000의 RCS는 SMS변경 안내 1건뿐, 금액 라벨 없어 skip).
- `18001111,하나,2222` → SMS `9*0*` 마스킹 1건만 매치 (-18,000원).
- `18001111,하나,1111` → SMS `0*7*` 3건 + RCS `0*7*` 1건 매치 (+60,000, -13,900×2, +959,100).
- 총합 동일 973,300원, 그러나 카드별로 정확히 분리됨.

### Phase 1.6 — 카드별 분리 표시 (완료)

규칙 (사용자 정의):
- 파라미터 3개 (`phone,id,last4`) → 카드번호까지 체크하고 **그룹 키를 `id(last4)`로 분리 표시**.
- 파라미터 2개 (`phone,id`) → 카드번호 무시, **그룹 키는 `id` 그대로** (카드회사 단위).

구현: `SMSReader`에 `groupKeyFor(configId, cardLast4)` 헬퍼 추가, SMS·RCS 양쪽 경로의 `groupMap.put(...)` 키로 사용.

UI 검증:
```
이번 달 총 승인     973,300원
하나(2222)          -18,000원
하나(1111)          991,300원
```

`현대` (`15776000,현대`)와 `삼성` (`15990501,삼성`)은 매치된 항목이 없어 그룹이 비어 화면에 표시되지 않음.

### 사이드 발견 — 삼성카드 발신번호

사용자가 등록한 `15990501`은 **삼성카드 결제일/월청구서 안내 전용** 발신번호:
- 본문에 `[삼성카드] 결제금액 안내` + `결제금액 / 511,899원` (이번 달 청구액).
- 라벨이 `결제금액`이라 우리 파서(`findAmountAfterLabel(texts, "금액")`)가 매치 안 함 → 자동 skip. **이게 의도에 맞는 동작** (지난 달까지 합계인 청구액을 이번 달 사용액에 잘못 더하면 안 됨).

삼성카드의 실제 거래 승인은 **`15888700`** 발신:
- SMS 인박스에 13건 (대부분 SMS 경로).
- RCS 1건.

→ 이번 달 거래 합산을 원하면 사용자 설정에 `15888700,삼성[,last4]` 줄을 추가해야 함.

### 미해결 / 후속

1. **다른 카드사 발신번호의 openrichcard 스키마 검증** — 18001111 외에 큰 표본이 있는 15447200(28건), 15889955(26건) 등이 같은 8 라벨(금액/카드/사용처/거래시간/...) 페어인지 1샘플 dump로 확인 필요. 다르면 발신번호별 라벨 매핑 분기 추가.
2. **SMS+RCS dedup 미적용** — 이번 달은 겹치는 결제건이 없어 무이슈. 카드사가 같은 결제를 두 채널로 보낼 가능성에 대비해 향후 `(거래시간, 금액, 사용처)` 합성키로 dedup 추가 검토.
3. **UI 금액 컬럼 세로 분할 버그** — 본문 Column이 너무 넓게 잡혀 우측 금액 Text가 1글자 폭으로 줄어듦. RCS 작업과 무관하지만 노출도 높아 우선순위 있음. `Row` 안에서 본문 Column에 `weight(1f)`, 금액에 적절한 minWidth/wrap 적용 필요.
4. **`probeRcsAccess` 제거 또는 보존 결정** — 매 진입 시 685행 쿼리는 가벼운 편이라 로깅 끄고 보존 가능. 운영 중 막힘 감지에는 유용.
5. **권한 미부여 시 동작** — `READ_SMS` 미부여 상태에서도 위젯/Worker가 RCS 쿼리를 호출하면 `cursor=null` 또는 SecurityException으로 빈 결과가 prefs를 덮어쓸 가능성 검토 (위젯 silent-failure 함정 §2.5 연장선).
6. **취소 RCS 출현 케이스** — 다른 단말/통신사에 존재할 수 있음. 발견 시 mediaUrl 또는 별도 라벨 분기 필요.

## 7. 위험 / 미확인 항목

- **다른 카드사의 openrichcard 스키마가 동일한지** — 18001111만 깊게 봤음. 15447200/15889955/0269589000 등 큰 표본이 있는 발신번호도 동일한 8 라벨 페어인지 추후 확인 필요. (다르면 카드사별 파서 분기.)
- **취소 RCS 카드의 존재 가능성** — 이 단말 18001111에는 0건이지만, 다른 카드사/단말에선 있을 수 있음. 만약 있다면 mediaUrl 또는 별도 키워드로 분기.
- **OS 업데이트로 `content://im/chat` 접근 정책 변경 가능성**. Probe 결과는 영구 보장되지 않음 — 권한 실패 시 자동으로 Phase 2로 fallback하는 방어 필요.
- **dedup 미검증** — SMS와 RCS 동일 결제 중복 수신 여부는 실제 데이터에서 다음 결제 발생 후 확인.
- 권한 미부여 상태(`READ_SMS` 거부)에서 RCS도 read 시도하면 silently empty가 될 수 있음 → 현재 위젯/Worker의 silent-failure 함정과 동일하게 prefs 갱신 가드 필요.

---

## 부록 A. 검증에 쓴 명령

```bash
# 18001111 RCS 전체 덤프
MSYS_NO_PATHCONV=1 adb -s R3CX705W62D shell \
  "content query --uri content://im/chat \
   --projection _id:address:date:content_type:body \
   --where \"address='18001111'\" --sort 'date DESC'" \
  > app/build/tmp/im_chat_18001111.txt

# content_type 분포
grep -oE 'content_type=[^,]+' app/build/tmp/im_chat_18001111.txt | sort | uniq -c

# 본문 text 토큰 빈도
grep -oE '"text":"[^"]+"' app/build/tmp/im_chat_18001111.txt | sort | uniq -c | sort -rn | head -40

# 959,100 매치 위치
grep -n '959,100' app/build/tmp/im_chat_18001111.txt
```

## 부록 B. 케이스 식별값 (959,100원)

| 항목 | 값 |
|---|---|
| `_id` | 653 |
| `thread_id` | (별도 확인 필요) |
| `address` | 18001111 |
| `date` (epoch ms) | 1775707544762 |
| `content_type` | `application/vnd.gsma.openrichcard.v1.0+json` |
| `card` | `open_rich_card` |
| `mediaUrl` | `o_3a0ca621fb1044308e9121d0.png` (표준) |
| layout text (라벨/값) | 금액/959,100원, 카드/하나0\*7\*, 손님명/차\*욱, 거래종류/신용, 거래구분/일시불, 사용처/(주)케이티알파, 거래시간/04/09 13:05, 누적금액/1,886,600원 |
