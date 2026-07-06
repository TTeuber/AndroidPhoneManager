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
  AllowedApp.kt     - Allowlist entry (packageName, source)
  BlacklistedApp.kt - Blacklist entry (packageName, reason)
  CooldownRequest.kt - Pending installation request with approval window

data/dao/
  BlockDao.kt       - Block CRUD, setScheduleActive()
  AppRuleDao.kt     - getBlockedPackageNames() critical query
  ScheduleDao.kt    - Schedule CRUD
  LockDao.kt        - Lock operations
  AllowedAppDao.kt  - Allowlist CRUD and queries
  BlacklistedAppDao.kt - Blacklist CRUD and queries
  CooldownRequestDao.kt - Cooldown request CRUD and status updates

data/database/
  SelfControlDatabase.kt  - Room DB (version 3)
  Converters.kt          - Type converters for Instant, enums

data/repository/
  BlockRepository.kt         - Main data access layer for blocks
  AppInstallationRepository.kt - Allowlist, blacklist, and cooldown management
```

### Domain Layer
```
domain/
  LockManager.kt           - Lock enforcement logic
  ScheduleManager.kt       - Schedule evaluation logic
  ContentRestrictionManager.kt - Chrome managed configurations (URL blocklist, SafeSearch, incognito)
  AppInstallationManager.kt - App installation evaluation and cooldown management
  PlayStoreParser.kt       - Parses Play Store pages to detect app categories
```

### Workers
```
worker/
  ScheduleWorker.kt            - Updates isScheduleActive periodically
  UnlockWorker.kt              - Checks for expired locks
  CooldownNotificationWorker.kt - Notifies when cooldown window opens
  CooldownExpirationWorker.kt   - Expires cooldown requests
```

### Services & Receivers
```
service/
  AppBlockingService.kt  - Foreground service that monitors/blocks apps
receiver/
  PackageChangeReceiver.kt - Detects app installations and triggers blocking updates
  SelfControlDeviceAdminReceiver.kt - Device admin receiver for Device Owner API
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
```

## Device Owner Setup

```bash
# Set device owner (required for app blocking to work)
adb shell dpm set-device-owner com.tyler.selfcontrol/.receiver.SelfControlDeviceAdminReceiver

# Check status
adb shell dumpsys device_policy
```

The app uses `DevicePolicyManager.setPackagesSuspended()` for blocking.

## App Blocking System

`AppBlockingService` combines three sources to determine which apps to block:

1. **Block Rules** - Apps added to user-created blocks (via `AppRuleDao.getBlockedPackageNames()`):
   ```kotlin
   SELECT DISTINCT packageName FROM app_rules
   WHERE blockId IN (
       SELECT id FROM blocks
       WHERE isEnabled = 1
       AND (state = 'ALWAYS_ON' OR (state = 'SCHEDULED' AND isScheduleActive = 1))
   )
   ```

2. **Blacklist** - Apps that can never be installed (from `BlacklistedApp` table)
   - Play Store, Facebook, Instagram, etc.
   - Managed via `AppInstallationRepository`

3. **Not on Allowlist** - Non-system apps not explicitly allowed
   - Only blocks apps installed after initial setup
   - System packages (com.android.*, com.google.*, android.*) are exempt
   - Apps must be on allowlist to run without being blocked

The service uses `DevicePolicyManager.setPackagesSuspended()` to suspend blocked apps and monitors the foreground app every second to immediately block attempts to open suspended apps.

## Build & Test

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # unit tests (domain logic: schedules, locks, parsing)
./gradlew lintDebug            # lint
```

Uses signing config from `keystore.properties` (even for debug builds to maintain device owner status). When `keystore.properties` is absent (e.g. CI), builds fall back to default debug signing.

Unit tests live in `app/src/test/` mirroring the source packages (`domain/`, `data/model/`). CI (`.github/workflows/android.yml`) runs tests, lint, and assembleDebug on every push.

## Implementation Status

- [x] Phase 1: Project Foundation & Device Owner Setup
- [x] Phase 2: Database & Block Model
- [x] Phase 3: App Blocking Core
- [x] Phase 4: Lock System
- [x] Phase 5: Schedule System
- [x] Phase 6: Website Blocking via Chrome managed configurations (URL blocklist, SafeSearch, incognito disabled)
- [ ] Phase 7: App Installation Control (Play Store parsing, cooldown)
- [ ] Phase 8: Security Hardening
- [ ] Phase 9: Polish & Testing

## App Installation Control (Phase 7)

**Status:** Partially implemented

The app controls which apps can be installed through an allowlist/blacklist system:

### Blocking Mechanism
- Apps installed without being on the allowlist are automatically blocked by `AppBlockingService`
- `PackageChangeReceiver` detects new installations and triggers blocking evaluation
- Dynamic BroadcastReceiver in `AppBlockingService` provides backup detection
- Retry mechanism handles cases where device owner becomes available after startup

### Play Store Parsing
- `PlayStoreParser` fetches and parses Play Store pages using Jsoup
- Category detection: Social, Entertainment, Video Players, Games, Browsers
- Game detection via "About This Game" section text
- Browser detection via keyword matching

### Cooldown System
- Restricted apps require 24-hour cooldown before installation
- Approval window: 3-6 PM next day
- `CooldownNotificationWorker` notifies when approval window opens
- `CooldownExpirationWorker` expires requests after window closes

### Database Models
- `AllowedApp` - Apps permitted for installation
- `BlacklistedApp` - Apps that can never be installed
- `CooldownRequest` - Pending installation requests with approval windows

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

- use `adb -e install -r -t app/build/outputs/apk/debug/app-debug.apk` to install the app to the emulator
