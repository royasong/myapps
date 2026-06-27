# AGENTS.md

MyCard is an Android card-spending app that parses SMS/Samsung RCS card notifications and shows totals in the app and widget.

## Commands

- Build: `.\gradlew.bat assembleDebug`
- Install: `.\gradlew.bat installDebug`
- Test: `.\gradlew.bat :app:testDebugUnitTest`
- Instrumented test: `.\gradlew.bat :app:connectedAndroidTest`
- Lint: `.\gradlew.bat lint`

## Architecture

- `notif/CardNotificationListener.kt`: notification listener entry point.
- `notif/NotifCardReader.kt`: notification parsing and card approval extraction.
- `notif/NotificationBasedCardActivity.kt`: main notification-based UI.
- `notif/db/NotificationDatabase.kt`, `NotificationDao.kt`, `NotificationEntity.kt`: Room storage.
- `notif/RawDump.kt`, `RawDumpAll.kt`: raw notification dump helpers.
- `config/card_filters.json`: version-controlled card filter source.
- `widget/CardWidgetProvider.kt`: home-screen widget.

## Rules

- Work only under `D:\workspace\mycard\MyCard`; do not inspect or edit sibling `arin`.
- PR base branch is `sach_dev` unless the user explicitly says otherwise.
- Keep user-facing strings Korean.
- Do not move `SMSReader.java` back to old `ui/theme` paths if merge conflicts mention it.
- Treat `config/card_filters.json` as the source for filter changes.
- Do not directly edit Room DB files; use app code/import paths.
- Version comes from the root `VERSION` file via `computedVersionCode` / `appVersion`.

## Verification

Run `.\gradlew.bat :app:testDebugUnitTest` for parser/filter changes. For notification, widget, or install behavior, also run `assembleDebug` and verify on a device.
