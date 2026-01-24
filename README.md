# Android Self Control App

An Android self-control app that helps users manage screen time by blocking distracting apps and websites using Device Owner APIs.

## Features

- **Block Apps** - Create named blocks containing apps and websites to restrict
- **Schedule Blocks** - Automatically activate blocks on specific days and times
- **Lock Blocks** - Prevent modification of blocks for a set duration
  - Lock until a specific date/time
  - Lock for a timer duration
  - Lock forever
- **Website Blocking** - Built-in browser with URL blocking and QR code scanner
- **App Installation Control** - Allowlist/blacklist system with 24-hour cooldown for restricted apps

## Tech Stack

- **Kotlin** 2.0.21
- **Jetpack Compose** - Modern declarative UI
- **Room** - Local database persistence
- **Hilt** - Dependency injection
- **WorkManager** - Background scheduling
- **DataStore** - Settings storage
- **Device Owner APIs** - App suspension and blocking
- **CameraX + ML Kit** - QR code scanning in browser

## Requirements

- Android 13+ (API level 33)
- Device must be set as Device Owner (see setup below)
- No existing Google account on device (for initial Device Owner setup)

## Setup

### 1. Clone and Build

```bash
git clone <repository-url>
cd SelfControl
./gradlew assembleDebug
```

### 2. Install on Device/Emulator

```bash
# For emulator
adb -e install -r -t app/build/outputs/apk/debug/app-debug.apk

# For physical device
adb -d install -r -t app/build/outputs/apk/debug/app-debug.apk
```

### 3. Set Device Owner (Critical)

The app **requires Device Owner status** to block apps. This must be done on a factory-reset device without any Google accounts.

```bash
# Set device owner
adb shell dpm set-device-owner com.tyler.selfcontrol/.receiver.SelfControlDeviceAdminReceiver

# Verify status
adb shell dumpsys device_policy
```

**Important:** Once Device Owner is set, it cannot be removed without a factory reset (or using the in-app "Clear Device Owner" button in Settings).

## Usage

### Creating a Block

1. Tap the "+" button on the main screen
2. Name your block (e.g., "Social Media", "Work Hours")
3. Add apps to block by tapping "Add Apps"
4. Optionally add websites to block
5. Toggle the block on/off as needed

### Scheduling a Block

1. Edit a block
2. Change state from "Always On" to "Scheduled"
3. Set the days and time range when the block should be active
4. The block will automatically activate/deactivate based on the schedule

### Locking a Block

1. Edit a block
2. Tap the lock icon
3. Choose lock mode:
   - **Until** - Lock until a specific date/time
   - **Timer** - Lock for a duration
   - **Forever** - Permanently lock (can only add restrictions, not remove)
4. Locked blocks cannot be disabled or have rules removed

### App Installation Control

- Apps are automatically evaluated when installed
- Restricted categories (social media, games, etc.) require 24-hour cooldown
- Approval window: 3-6 PM the next day
- Blacklisted apps (Play Store, Facebook, Instagram) are never allowed

## Architecture

```
app/src/main/java/com/tyler/selfcontrol/
├── data/              # Data layer
│   ├── model/         # Room entities (Block, Schedule, Lock, etc.)
│   ├── dao/           # Database access objects
│   ├── database/      # Room database setup
│   └── repository/    # Data repositories
├── domain/            # Business logic
│   ├── LockManager.kt
│   ├── ScheduleManager.kt
│   ├── UrlBlocker.kt
│   └── AppInstallationManager.kt
├── service/           # Background services
│   └── AppBlockingService.kt
├── worker/            # WorkManager workers
│   ├── ScheduleWorker.kt
│   ├── UnlockWorker.kt
│   └── CooldownNotificationWorker.kt
├── receiver/          # Broadcast receivers
└── ui/                # Compose UI
    ├── screens/       # Screen composables
    ├── viewmodel/     # ViewModels
    └── browser/       # Custom browser with blocking
```

## How It Works

### App Blocking

The app uses `DevicePolicyManager.setPackagesSuspended()` to block apps. Three sources determine which apps are blocked:

1. **Block Rules** - Apps in enabled blocks
2. **Blacklist** - Permanently banned apps
3. **Not on Allowlist** - Non-system apps not explicitly allowed

`AppBlockingService` runs as a foreground service, monitoring the foreground app every second and immediately blocking access to suspended apps.

### Block Activation Logic

Apps are blocked when:

```kotlin
block.isEnabled == true &&
(block.state == ALWAYS_ON || block.isScheduleActive == true)
```

### Schedule System

- `ScheduleWorker` evaluates schedules every 1-5 minutes
- Days stored as bitmask (bit 0 = Sunday through bit 6 = Saturday)
- Times stored as minutes from midnight (0-1439)
- Supports overnight schedules

### Lock System

- `UnlockWorker` checks for expired locks periodically
- Locked blocks allow adding restrictions but not removing them
- Lock modes: `UNLOCKED`, `UNTIL_DATETIME`, `TIMER`, `FOREVER`

## Development

### Database Migrations

When adding entities or modifying the schema:

1. Create model in `data/model/`
2. Create DAO in `data/dao/`
3. Add entity to `SelfControlDatabase`
4. Increment `DATABASE_VERSION`
5. Provide DAO in `di/DatabaseModule.kt`

### Building

The app uses a signing config from `keystore.properties` for both debug and release builds to maintain Device Owner status across rebuilds.

## Known Limitations

- Requires Device Owner status (factory reset needed to set up)
- Cannot be uninstalled without clearing Device Owner status
- Some system apps cannot be blocked
- Play Store parsing may fail if page structure changes
