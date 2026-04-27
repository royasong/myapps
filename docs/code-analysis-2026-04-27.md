# 코드 분석 요약 (2026-04-27)

## 저장소
한 git 루트 안에 **독립된 두 개의 안드로이드 앱**이 들어 있습니다. 공통 빌드 없음.

| 항목 | `arin/` | `MyCard/` |
|---|---|---|
| 패키지 | `com.arin.app` | `com.example.mycard` |
| 목적 | 가족용 SMS/통화 런처 | 카드 승인 SMS 집계 + 홈 위젯 |
| UI | XML View + AppCompat | Jetpack Compose |
| Kotlin / AGP | 1.9.0 / 8.6.1 | 2.0.21 / 8.11.2 |
| compileSdk / minSdk | 35 / 33 | 36 / 34 |
| 영구 저장 | `filesDir` 평문 파일 | SharedPreferences `mycard_prefs` |

---

## arin — 가족용 SMS/통화 런처

**구성**: `MainActivity` → `SettingActivity` / `EditSmsActivity` (3개)

**저장 파일** (`filesDir`):
- `bg_color.txt`, `btn_color.txt` — 배경/버튼 색
- `sms.txt` — 빠른-전송 4문구
- `arin_bg.png` — 배경 이미지

**주요 이슈**:
1. 엄마/아빠 전화번호가 `MainActivity.kt:41-42`에 하드코딩
2. **SEND_SMS / CALL_PHONE 런타임 권한 요청 없음** → 첫 실행 시 크래시
3. `SmsManager.getDefault()` (deprecated) 사용
4. `SettingActivity`의 launcher 등록 메서드가 두 벌 (`activityResultActivityRauncher` / `registerBackgroundBgPicker`) — 후자는 dead

---

## MyCard — 카드 승인 SMS 집계 + 위젯

**데이터 흐름**: `mycard_prefs`(SharedPreferences)가 Activity·Worker·Widget 세 곳의 **단일 진실 공급원**.

**핵심 모듈**:
- `SMSReader.java` — 이번 달 `[Web발신]` 카드 승인 SMS를 읽어 `cardGroup` 설정(`phone,id` per line)에 따라 그룹화. `자동결제` → `승인` 치환. 취소는 음수 처리.
- `MainActivity` (Compose) — SMS 새로고침 + `widget_total` / `widget_groups` 업데이트
- `CardWidgetProvider` — RemoteViews 위젯, `WIDGET_REFRESH` 브로드캐스트로 단독 갱신
- `CardRefreshWorker` — 같은 작업 + 텍스트 백업, **현재 어디서도 스케줄 안 됨**(dead)
- `SettingsActivity` — `cardGroup`, `memo` 입력 (memo는 읽히지 않음)

**주요 이슈**:
1. **`MainActivity.kt:208`의 prefs 이름 오타** `"l\`"` → 새로고침 버튼이 위젯에 반영 안 됨 (실제 버그)
2. **prefs 갱신 블록이 4중 복붙** → 함수 추출 필요
3. `widget_groups` JSON을 손으로 만들어 정규식으로 파싱 — Gson 의존성은 추가됐지만 미사용
4. `SMSReader.java`가 `ui/theme/` 폴더에 잘못 위치
5. `SMSReader`의 `findCardIdByPhone` / `findCardGroup` 메서드는 호출처 없음 (dead)
6. `MainActivity` import에 WorkManager 잔재 다수

---

## 우선 수정 권장 (영향도 순)

1. `MyCard/MainActivity.kt:208`의 `"l\`"` → `"mycard_prefs"` — **실버그**
2. MyCard의 prefs+위젯 갱신 블록 함수 추출 — 회귀 방지
3. arin의 SEND_SMS / CALL_PHONE 런타임 권한 요청 추가 — 크래시 방지
4. arin의 `SmsManager` API 마이그레이션
5. dead code / 미사용 import / 미사용 의존성 정리
