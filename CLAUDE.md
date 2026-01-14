# Self-Control App - Claude Context

## Project Overview

Android self-control app using **Device Owner APIs** to block apps and websites. Users can create "blocks" containing app/website rules, enable them via toggle, lock them for a duration, and schedule automatic activation.

**Package:** `com.tyler.selfcontrol`
**Min SDK:** 33 (Android 13+)
**Database Version:** 3

## Tech Stack

- Kotlin 2.0.21 + Jetpack Compose
- Room for persistence
- Hilt for dependency injection
- WorkManager for background scheduling
- DataStore for settings

## Core Concepts

### Block System
A **Block** is a named group of app/website rules with:
- `isEnabled` - User toggle (can be freely changed unless locked)
- `state` - `ALWAYS_ON` or `SCHEDULED`
- `isScheduleActive` - For scheduled blocks, whether the schedule is currently active

**Blocking Logic:** Apps are blocked when:
```
isEnabled = true AND (state = ALWAYS_ON OR isScheduleActive = true)
```

### Lock System
Locks prevent modification of a block:
- Modes: `UNLOCKED`, `UNTIL_DATETIME`, `TIMER`, `FOREVER`
- Locked blocks can ADD restrictions but cannot REMOVE them
- `UnlockWorker` checks for expired locks

### Schedule System
- Days stored as bitmask (bit 0 = Sunday through bit 6 = Saturday)
- Times stored as minutes from midnight (0-1439)
- Supports overnight schedules (when endTime < startTime)
- `ScheduleWorker` updates `isScheduleActive` every 1-5 minutes

## Key Files

### Data Layer
```
data/model/
  Block.kt          - Block entity with BlockState enum
  Schedule.kt       - Schedule entity with day bitmask helpers
  Lock.kt           - Lock entity with LockMode enum
  AppRule.kt        - App blocking rule (packageName)
  WebsiteRule.kt    - Website blocking rule (domain, path)

data/dao/
  BlockDao.kt       - Block CRUD, setScheduleActive()
  AppRuleDao.kt     - getBlockedPackageNames() critical query
  ScheduleDao.kt    - Schedule CRUD
  LockDao.kt        - Lock operations

data/database/
  SelfControlDatabase.kt  - Room DB (version 3)
  Converters.kt          - Type converters for Instant, enums

data/repository/
  BlockRepository.kt     - Main data access layer
```

### Domain Layer
```
domain/
  LockManager.kt      - Lock enforcement logic
  ScheduleManager.kt  - Schedule evaluation logic
  UrlBlocker.kt       - URL matching and blocking logic for browser
```

### Workers
```
worker/
  ScheduleWorker.kt   - Updates isScheduleActive periodically
  UnlockWorker.kt     - Checks for expired locks
```

### Services
```
service/
  AppBlockingService.kt  - Foreground service that monitors/blocks apps
```

### UI Layer
```
ui/screens/
  MainScreen.kt        - Block list with toggles
  BlockEditScreen.kt   - Edit block: apps, websites, schedule, lock
  SettingsScreen.kt    - Dev mode, clear device owner

ui/viewmodel/
  MainViewModel.kt     - Block list operations
  BlockEditViewModel.kt - Block editing operations
  SettingsViewModel.kt  - Settings + worker scheduling

ui/browser/
  BrowserActivity.kt        - Utility browser with QR scanner
  BlockingWebViewClient.kt  - WebViewClient with URL blocking
  QrScannerView.kt          - CameraX + ML Kit barcode scanner
```

## Device Owner Setup

```bash
# Set device owner (required for app blocking to work)
adb shell dpm set-device-owner com.tyler.selfcontrol/.receiver.SelfControlDeviceAdminReceiver

# Check status
adb shell dumpsys device_policy
```

The app uses `DevicePolicyManager.setPackagesSuspended()` for blocking.

## Important Queries

The critical query for determining which packages to block is in `AppRuleDao.kt`:
```kotlin
SELECT DISTINCT packageName FROM app_rules
WHERE blockId IN (
    SELECT id FROM blocks
    WHERE isEnabled = 1
    AND (state = 'ALWAYS_ON' OR (state = 'SCHEDULED' AND isScheduleActive = 1))
)
```

## Build

```bash
./gradlew assembleDebug
```

Uses signing config from `keystore.properties` (even for debug builds to maintain device owner status).

## Implementation Status

- [x] Phase 1: Project Foundation & Device Owner Setup
- [x] Phase 2: Database & Block Model
- [x] Phase 3: App Blocking Core
- [x] Phase 4: Lock System
- [x] Phase 5: Schedule System
- [x] Phase 6: Custom WebView Browser (ML Kit QR, URL blocking)
- [ ] Phase 7: App Installation Control (Play Store parsing, cooldown)
- [ ] Phase 8: Security Hardening
- [ ] Phase 9: Polish & Testing

## Phase 7 Notes (Next Up)

App installation control with:
- Play Store link parsing with Jsoup
- Category detection (Social, Entertainment, Video Players)
- Game detection ("About This Game" text)
- Browser detection (keyword matching)
- Cooldown queue for restricted apps
- Play Store suspension/unsuspension

## Common Patterns

### Adding a new entity:
1. Create model in `data/model/`
2. Create DAO in `data/dao/`
3. Add to `SelfControlDatabase` entities list
4. Increment database version
5. Add DAO provider in `di/DatabaseModule.kt`
6. Add operations to `BlockRepository.kt` if needed

### Hilt Workers:
Use `@HiltWorker` + `@AssistedInject` pattern (see `ScheduleWorker.kt`)

---

## Notes from Dev

- Don't try to install/stop/restart the app on the emulator because device owner permissions won't let you
    - Instead, ask me to do it
