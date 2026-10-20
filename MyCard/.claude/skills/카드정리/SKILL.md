---
name: 카드정리
description: |
  카드사 앱(하나·현대·신한)을 adb로 직접 띄워 이번 달 실적을 수집하고, MyCard 집계와 건별 대조한 뒤
  차이가 있으면 필터 수정 또는 수동 항목 보정 → ⋮ 업데이트 → 재대조 → docs 정리까지 수행한다.
  사용자가 "카드정리", "카드 정리하자", "실적 대조", "카드사 앱이랑 비교해줘"라고 할 때 사용.
---

# 카드정리

카드사 앱 실적 ↔ MyCard 대조 자동화. 작업 디렉터리는 `D:\workspace\mycard\MyCard`.

## 고정 규칙 (사용자 지정 — 바꾸지 말 것)

- **대상 카드 5장만**: 하나 매직 9207, 하나 브로드 0179, 네이버 현대카드, SK 현대카드, 신한 이마트 8423. 롯데·현백 제외.
- **기간은 현재 달만** (이번 달 1일 ~ 오늘). 지난 달은 하지 않는다.
- **카드사 앱이 무조건 정답.** "카드사 미반영이라 MyCard가 맞다" 판정 금지.
  단, 당일·전일 결제처럼 카드사 실적에 아직 안 올라온 건은 사용자에게 보여주고 결정을 받는다 (2026-10 기준 답: "기다리면 된다").
- 카드사 앱을 띄우는 것도, 메뉴를 찾아가는 것도 Claude가 한다. **로그인이 필요할 때만** 사용자에게 요청하고 기다린다.
- 동의/약관 팝업에는 동의하지 않는다 (닫기). 광고 팝업은 닫는다.
- 이 스킬에서는 데이터 수집을 위한 스크린샷 판독이 허용된다 (빌드 검증용 스크린샷 금지 규칙과 별개).

## 0. 준비

1. `adb devices -l`. 하나면 그 단말 사용, 여러 대면 사용자에게 묻는다.
2. 디스플레이 확인: `adb shell dumpsys display | grep -E "DisplayDeviceInfo|mState="` — state ON인 디스플레이 ID를 쓴다.
   폴드7(`R3CY70RDW3B`) 접힘 = 커버 `4630946872173396372` (1080x2520). 캡처는 항상 `screencap -d <id>`.
3. 작업 폴더: scratchpad 아래 `recon-YYYYMMDD/`. 수집 결과·DB 복사본·백업은 전부 여기에 둔다 (repo에 넣지 않음).
4. **화면 꺼짐 방지** (진행 중 잠금화면으로 빠지지 않게): 원래 값을 기록해 두고 USB 충전 중 화면 유지를 켠다.
   ```bash
   ORIG=$(adb -s $S shell settings get global stay_on_while_plugged_in)   # 보통 0
   echo "$ORIG" > <work>/stay_on_orig.txt
   adb -s $S shell svc power stayon usb
   adb -s $S shell input keyevent KEYCODE_WAKEUP
   ```
   USB 연결(충전) 중일 때만 효과가 있다 (`dumpsys battery`의 `USB powered: true` 확인).
5. 화면이 잠겨 있으면 (uiautomator dump에 시계/잠금화면만 보임) 사용자에게 잠금 해제 요청.

## 1. 카드사 앱 실적 수집

**방법**: 세 앱 모두 WebView 디버깅이 꺼져 있어 CDP 불가, 하나카드는 uiautomator에 WebView 내용이 안 잡힌다.
→ `screencap` → Read로 판독 → `input tap/swipe` 좌표 조작. 이미지 표시 배율(예: 857 표시 → ×1.26)을 좌표에 곱한다.
여러 페이지는 swipe 후 연속 캡처, 필요하면 PIL로 잘라 이어붙여 한 번에 판독한다.

앱 실행: `adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1`, 재시작은 `am force-stop` 후 실행.
뒤로가기를 너무 누르면 앱 종료 팝업이 뜬다 → "더 이용하고 싶어요"/X로 닫기.

| 카드 | 앱 / 경로 | tsv key |
|---|---|---|
| 하나 9207 / 0179 | `com.hanaskcard.paycla` (자동로그인 설정됨). 메인 중간 **카드별 실적** → 카드 탭 → 혜택 항목(9207: "실적 30만원", 0179: "Life서비스") → **실적 충족 상세내역** (이용금액 N건 표시) | `hana_9207`, `hana_0179` |
| 네이버 / SK 현대 | `com.hyundaicard.appcard`. 메인 실적 카드 → 실적 달성 현황 → **다음 달** 탭 (실적 합산 기간이 이번 달인지 확인) → 실적 상세 내역 → 다음 달 탭. 카드 전환: 카드명 옆 ▼ | `hyundai_naver`, `hyundai_sk` |
| 신한 8423 | `com.shcard.smartpay`. 홈 → **내 카드 실적 보기** → 캐러셀에서 "WISE & SHOPPING 이마트"(2026-10 기준 2/6) → 실적 상세보기 → 다음달 혜택 펼침 (산정기간 확인) → **산정내역** | `shinhan_8423` |

주의:
- 현대카드 앱은 실행 직후 동의·유료 서비스 가입 팝업(예: "자산 서비스 동의", "피싱케어Plus")이 뜰 수 있다 → 항상 하단 **[닫기]**만 누른다.
- 현대카드 카드 전환(네이버 → SK)은 **실적 달성 현황 요약 화면**(카드명 ▼가 보이는 화면)에서만 된다. 상세 내역에서 뒤로가기 후 반드시 캡처로 요약 화면인지 확인하고 ▼를 누른다. 탭을 연속으로 묶어 보내지 말고 단계마다 확인한다.
- 하나카드 메인의 "이용내역"(최근 일주일)이나 결제예정금액은 기준이 아니다. **카드별 실적** 화면이 기준.
- 현대카드 **"이번 달" 탭 = 전월 사용분**이다. 반드시 "다음 달" 탭.
- 취소는 음수로 기록한다.

각 카드마다 `<work>/<key>.tsv`에 `YYYY-MM-DD<TAB>가맹점<TAB>금액` 한 줄씩 저장하고,
**건수·합계가 화면의 총 이용금액(N건)과 정확히 일치하는지** `awk -F'\t' '{s+=$3}END{print NR, s}'`로 확인한다. 안 맞으면 다시 읽는다.

## 2. MyCard 데이터 복사 (읽기 전용)

```bash
S=<serial>; export MSYS_NO_PATHCONV=1; cd <work>
for f in notifications.db notifications.db-wal notifications.db-shm; do adb -s $S exec-out run-as com.example.mycard cat databases/$f > $f; done
adb -s $S pull /sdcard/Documents/MyCard/raw_notifications_all_<YYYY>_<MM>.jsonl .
```

DB를 단말로 되돌려 쓰는 것은 금지 (CLAUDE.md "Room DB 직접 수정 금지").

## 3. 대조

```bash
python .claude/skills/카드정리/scripts/recon.py --work <work>
```

출력: 카드별 카드사/MyCard 합계·차이, `[카드사에만]`(원인 힌트 포함), `[MyCard에만]`, `DB 최초 ts`, `<work>/missing.json`.
결과를 표로 사용자에게 보고한다.

## 4. 원인별 조치 — 실행 전 사용자 승인

| 증상 | 원인 | 조치 |
|---|---|---|
| `[카드사에만]` + "알림 있음 → 필터 문제" | 알림 포맷 변경/룰 부재 | CLAUDE.md **"파싱하자" 워크플로우**: `config/card_filters.json`에 새 필터를 **뒤에 덧붙임**(기존 수정보다 v2 추가 선호) → Python으로 매칭+같은 패키지 광고 비매칭 검증 → 승인 → `adb push config/card_filters.json /sdcard/Documents/MyCard/card_filters.json` → `docs/data/<카드사>.jsonl` 아카이브 + `docs/card-filter-<카드사>-<날짜>.md` |
| `[카드사에만]` + "알림 없음" | 수집 공백 (단말 교체·재설치, 리스너 꺼짐 등) | 수동 항목 보정 (아래) |
| `[MyCard에만]` 당일·전일 건 | 카드사 실적 반영 지연 | 사용자에게 보여주고 결정 (기본: 대기) |
| `[MyCard에만]` 그 외 | 오탐/취소 누락 | 기준은 카드사 앱. 원인(취소 룰 부재 등) 조사 후 필터로 해결 우선, 안 되면 사용자 확인 후 알림 기반 보기에서 수정/삭제 |
| 미파싱 0건인데 리스트가 비어 보임 | `card_filters.json` 없음 | 로그 `CardFilterStore: readShared: ... exists=false` 확인 후 push |

### 수동 항목 보정 절차 (단말 파일 수정 — 반드시 승인 후)

```bash
F=/sdcard/Documents/MyCard/raw_notifications_<YYYY>.jsonl
adb -s $S shell am force-stop com.example.mycard           # RawDump 메모리 캐시가 덮어쓰지 않도록 먼저
adb -s $S pull $F <work>/raw_before.jsonl && cp <work>/raw_before.jsonl <work>/raw_backup_<YYYYMMDD>.jsonl
python .claude/skills/카드정리/scripts/append_manual.py --work <work> --raw <work>/raw_before.jsonl --out <work>/raw_after.jsonl
adb -s $S push <work>/raw_after.jsonl $F
```

- `append_manual.py`는 missing.json 중 **알림 없는 건만** 넣는다. 시각은 거래일 12:00 KST + 순번(분) — 카드사 실적엔 시각이 없음.
- 같은 수동 항목이 이미 raw에 있는지(이전 카드정리에서 넣었는지) 먼저 확인해 중복 추가하지 않는다.

## 5. 업데이트 실행 → 재대조

1. `adb shell am start -n com.example.mycard/.MainActivity`
2. `uiautomator dump` → `text="⋮"` bounds 탭 → `text="업데이트"` bounds 중심 탭 (MyCard는 Compose라 dump로 좌표가 잡힌다).
3. `adb logcat -d -s UpdateAction | grep -E "start|loaded|done"` 로 `rebuildFromRaw: done` 확인.
4. 2단계(DB 복사) + 3단계(recon.py) 다시 실행 → 남은 차이가 사용자가 승인한 것(반영 대기 등)뿐인지 확인.

## 6. 문서 정리

- `docs/card-total-check-<YYYY-MM-DD>.md` — 단말·시각, 카드별 표(카드사 / 수정 전 / 수정 후), 원인, 조치(추가한 수동 항목 표 또는 필터), 남은 차이와 사용자 결정, 앱 경로가 바뀌었으면 갱신 내용. 예시: `docs/card-total-check-2026-10-17.md`.
- 필터를 바꿨으면 카드 룰 문서 + `docs/data/<카드사>.jsonl` (CLAUDE.md 규칙).
- 카드사 앱 화면 경로가 바뀌었으면 이 SKILL.md의 경로 표를 갱신한다.
- 커밋/PR은 사용자 요청 시: `feature/<topic>` 브랜치 → base `sach_dev`. 코드 변경이 있으면 VERSION/CHANGELOG bump.

## 완료 보고

먼저 화면 꺼짐 방지를 원복한다 (중간에 중단돼도 반드시):
`adb -s $S shell settings put global stay_on_while_plugged_in $(cat <work>/stay_on_orig.txt)` (원래 0이었으면 `svc power stayon false`와 동일).

카드별 최종 표 + 조치 내역 + 남은 차이 + 문서 경로 + (단말 파일을 수정했다면) 백업 위치.
