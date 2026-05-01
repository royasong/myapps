# 알림 dedupe + 카드별 그룹핑 (filterId) — 2026-05-01

## 배경

하나카드(`com.hanaskcard.paycla`) 결제 알림을 분석하던 중, 같은 거래에 대해 알림이 **두 개씩 적재**되는 현상을 발견했습니다.

### 같은 거래의 두 알림 비교

| 필드 | A 버전 | B 버전 |
|---|---|---|
| `ts` | 1777596118250 | 1777596118254 (4ms 후) |
| `template` | `BigTextStyle` | `InboxStyle` |
| `largeIcon` | null | 비트맵 115×115 |
| `textLines` (Inbox 전용) | 없음 | 본문 줄 |
| `title.big` (확장 헤더) | 없음 | "(결제) 191,000원" |
| `appInfo` 인스턴스 해시 | 다름 | 다름 |

`title` / `text` / `bigText`는 **완전히 동일**. 카드사 앱이 같은 거래에 대해 알림 스타일을 달리하여 두 번 post하는 패턴 (잠금화면 vs 알림 센터, 또는 펼치기 전/후를 다르게 보이려는 의도로 추정).

`NotificationListenerService`는 둘 다 잡으므로 그대로 DB에 적재되고, 합계가 **2배로 잡히는** 영향이 있었습니다.

또한, 같은 패키지(`com.hanaskcard.paycla`)에 카드 두 장(브로드 0179, 매직 9207)이 등록되어 있을 때 `groupByCompany`가 패키지 단위로 매핑하여 **두 카드가 한 그룹으로 합쳐지는** 별개 버그도 함께 확인됐습니다.

## 해결

### 1) Dedupe — `(pkg, title, text, bigText)` UNIQUE index + `IGNORE`

**`db/NotificationEntity.kt`**
```kotlin
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["ts"]),
        Index(value = ["pkg", "title", "text", "bigText"], unique = true)
    ]
)
data class NotificationEntity(
    ...
    val filterId: String? = null   // Task 2 컬럼
)
```

**`db/NotificationDao.kt`**
```kotlin
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insert(entity: NotificationEntity): Long
```

**`db/NotificationDatabase.kt`** — version 1 → 2, `fallbackToDestructiveMigration()`.

**키 선정 근거:** ts는 4ms 차이로 두 알림이 다르게 잡히므로 unique 키에 부적합. `(pkg, title, text, bigText)`는:
- BigText/Inbox 듀얼 케이스: title/text/bigText 모두 동일 → 정확히 dedupe 됨.
- 같은 가맹점·금액에 시각만 다른 두 결제: text에 `누적이용금액`이 들어가 매번 다름 → 정상적으로 별개 row로 적재됨 (위양성 dedupe 없음).

### 2) Listener / UpdateAction — IGNORE 결과 처리

**`notif/CardNotificationListener.kt`** — `dao.insert()` 결과가 `-1L`이면 raw dump skip + `dedupe-ignored` 로그.

**`notif/UpdateAction.kt`** — `rebuildFromRaw`에서도 동일. `rebuilt`/`parsed` 카운터는 실제 insert된 row만 계산.

### 3) filterId 저장 + 카드별 그룹핑

**`CardParser.parse()`**가 이미 `ParseResult.filterId`를 반환하고 있었음. 이를 entity에 저장:

```kotlin
baseEntity.copy(
    amount = parsed.amount,
    merchant = parsed.merchant,
    parsedAt = System.currentTimeMillis(),
    filterId = parsed.filterId
)
```

**`notif/NotificationBasedCardActivity.kt::groupByCompany`** — 그룹 키를 `pkg → cardCompany`에서 `filterId → cardCompany`로 변경 (pkg는 fallback):

```kotlin
.groupBy {
    it.filterId?.let { fid -> filterIdToCompany[fid] }
        ?: pkgToCompany[it.pkg]
        ?: it.pkg
}
```

이제 같은 패키지의 0179/9207이 각자의 `cardCompany`("하나 브로드 0179", "하나 매직 9207")로 분리 표시됩니다.

## DB 마이그레이션 정책

`fallbackToDestructiveMigration()` 채택 — 사유:
- 모든 알림 raw 데이터는 `raw_notifications.jsonl`(외부 저장소)에 별도 보존.
- 메뉴 3 (업데이트) 한 번이면 raw에서 DB가 완전히 재구성됨.
- 따라서 schema 변경 시 데이터 손실이 없고, manual migration 작성 비용이 낭비.

## 검증

빌드 + 설치 후:
1. 메뉴 3 (업데이트) 실행 → `rebuilt=14 parsed=10 skipBL=3 skipParseFail=3` (raw 14개 객체 → dedupe + 매칭).
2. 합계가 2배로 잡히지 않음 (191,000원 1건만 적재).
3. "하나 브로드 0179" / "하나 매직 9207" 두 그룹으로 분리 표시.

로그 확인 한 줄:
```
adb logcat -d --pid=$(adb shell pidof com.example.mycard) -s CardFilterStore CardParser UpdateAction CardNotifListener
```

## 함께 추가된 파일

`MyCard/docs/data/하나카드.jsonl` — 0179/9207 결제 알림 raw 6건 (dedupe 검증 코퍼스로 활용). 카드사 단위 raw 아카이브 정책에 따름 (`MyCard/CLAUDE.md` 참고).

## 알려진 한계

- `(pkg, title, text, bigText)` UNIQUE 키는 빈 알림(title=""·text=""·bigText="") 이 같은 패키지에서 여럿 들어올 경우 첫 번째 외엔 IGNORE됨. 정상 거래 알림은 본문에 누적이용금액 등이 들어가 영향 없음. raw_notifications_all.jsonl(blacklist 무관 모든 dump)에는 그대로 보존.
- DB는 destructive migration이므로, 향후 schema 변경 시 사용자에게 메뉴 3 재실행이 필요함 (raw 파일은 유지).
