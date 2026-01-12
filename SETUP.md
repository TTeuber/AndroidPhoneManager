# Setup Instructions

## Building and Running the App

### Prerequisites
- Android Studio (or command line tools)
- Android SDK API 36
- Emulator or physical device with API 33+ (Android 13+)

### First Time Setup

The project is already configured and should work out of the box in Android Studio. The build system will automatically:
- Use Java 21 (configured in `gradle.properties`)
- Load keystore credentials from `keystore.properties`
- Sign both debug and release builds

### Running in Android Studio

1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Select your emulator or connected device
4. Click Run ▶️

The app will build and install automatically.

### Setting as Device Owner

After installing the app, you need to set it as device owner using ADB:

**Important:** Device must be factory reset with no Google accounts added!

```bash
adb shell dpm set-device-owner com.tyler.selfcontrol/.receiver.SelfControlDeviceAdminReceiver
```

You should see: `Success: Device owner set to package com.tyler.selfcontrol`

### Verifying Device Owner Status

After setting device owner, open the app:
- Main screen should show "Device Owner Active" in green
- Settings should show dev mode toggle
- When dev mode is enabled, you'll see a "Clear Device Owner" button

### Development Workflow

**Dev Mode:**
- Enable dev mode in Settings
- This shows the "Clear Device Owner" button for easier testing
- You can clear device owner without factory reset during development

**Clearing Device Owner for Testing:**
1. Enable dev mode in Settings
2. Tap "Clear Device Owner" button
3. App is no longer device owner
4. You can now uninstall or modify as needed

### Building from Command Line

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install to connected device
./gradlew installDebug
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Common Issues

**"Device owner can only be set on factory reset device"**
- Factory reset your test device/emulator
- Don't add any Google accounts during setup
- Then run the ADB command

**"Not allowed to set device owner"**
- Make sure no user accounts are on the device
- Check that the app is installed
- Verify the package name matches

**Build fails with Java version error**
- The project is configured to use Java 21
- If you get Java errors, check that `/usr/lib/jvm/java-21-openjdk` exists
- Update `gradle.properties` with your Java 21 path if different

### Files Not in Git

These files contain secrets and are git-ignored:
- `keystore.properties` - Keystore passwords
- `selfcontrol.keystore` - Signing key
- `.env` - Environment variables

Make sure to back these up separately!
