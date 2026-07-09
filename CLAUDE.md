# Self-Control App - Claude Context

## Device Owner Setup

```bash
# Set device owner (required for app blocking to work)
adb shell dpm set-device-owner com.tyler.selfcontrol/.receiver.SelfControlDeviceAdminReceiver

# Check status
adb shell dumpsys device_policy
```

## Build & Test

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # unit tests (domain logic: schedules, locks, parsing)
./gradlew lintDebug            # lint
./gradlew detekt               # static analysis (config/detekt/detekt.yml; legacy findings in app/detekt-baseline.xml)
```

Uses signing config from `keystore.properties` (even for debug builds to maintain device owner status). When `keystore.properties` is absent (e.g. CI), builds fall back to default debug signing.

## Deploying

- use `adb -e install -r -t app/build/outputs/apk/debug/app-debug.apk` to install the app to the emulator
