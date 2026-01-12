# Self-Control Device Owner App - Complete Implementation Plan

## Project Overview

### Goal

Create a self-control device owner app for Android that blocks apps and websites in a way that is as difficult as possible for the user to unblock them. The phone becomes a utility device, with complex browsing and web tasks handled on a laptop.

**Key Capabilities:**
- Close apps that aren't allowed using Device Owner APIs
- Include a controlled utility browser that can block websites
- Block Chrome and prevent other browsers from being installed
- Use Device Owner APIs for enforcement that can't be easily bypassed

**Design Philosophy:**
This approach prioritizes high friction for distracting content while keeping the phone functional for utility tasks. The assumption is you're motivated to make it work initially, then the restrictions keep future-you disciplined. The phone becomes a utility device, not an entertainment device.

---
## Core Design Decisions

### Browser Approach

**Decision: Custom WebView "Utility Browser"**

Rather than implementing a local VPN to filter traffic through other browsers, the app includes a controlled WebView-based browser and blocks all other browsers.

**Rationale:**
- VPN implementation is complex (packet inspection, DNS filtering, certificates, edge cases)
- WebView gives complete control over URL loading via `shouldOverrideUrlLoading` and `shouldInterceptRequest`
- Simpler to maintain

**Browser Characteristics:**
- **Primary interface is QR scanner** – mental model is "scan this specific thing" rather than "browse the web"
- **URL bar hidden by default** – requires deliberate action to manually enter a URL
- **Minimal features** – no need for password manager integration, extensions, or sophisticated web app support

**Browser Implementation Details:**

*QR Scanner:*
- Use Google ML Kit library for QR scanning

*URL/Content Blocking:*
- Redirect from allowed → blocked: intercept in `shouldOverrideUrlLoading`, block the redirect
- JavaScript loading blocked domains: intercept in `shouldInterceptRequest`, return empty response
- JavaScript popup windows: blocked by default WebView setting

*Session Management:*
- Clear WebView cache/cookies on app start for simplicity
- No history, no tabs, no bookmarks - single session each time
- Fresh slate each browser launch
- Should remember nothing between sessions

*Security Settings:*
- JavaScript: enabled (too many sites break without it)
- User agent: default WebView user agent
- WebView debugging: disabled in production, enabled in dev mode
- Use HTTPS by default, allow HTTP if site doesn't support HTTPS (some utility sites might not)

*Downloads:*
- Implemented via `DownloadListener` and `DownloadManager`
- Not a priority but functional when needed

**Accepted Limitations:**
- No "Sign in with Google" (OAuth flows don't work in WebView)
- Some sites may behave differently
- If something doesn't work, do it on laptop instead

---

## Feature Specifications

### Blocks and Locks System

#### Blocks

Blocks contain the rules and can be locked. For example, one block might be called "Distractions" and block the YouTube and Netflix apps and websites.

**Website Blocking Features:**
- Support for specific paths: e.g., block `youtube.com/shorts` but not the rest of YouTube
- Allow lists for websites: e.g., block `twitch.tv` but allow `twitch.tv/northernlion`

**URL Matching Logic:**
- Blocking `youtube.com` blocks ALL subdomains (`m.youtube.com`, `www.youtube.com`, etc.)
- Path matching is prefix-based: `youtube.com/shorts` blocks `youtube.com/shorts?v=123` and `youtube.com/shorts/abc`
- Case insensitive
- HTTP and HTTPS both blocked
- Allowlist entries are exact domain + path prefixes, checked AFTER blocklist
- Support basic wildcards: `*.reddit.com` blocks all Reddit subdomains

**Block States:**
- Disabled
- Always on
- Schedule-based with ability to select:
  - Days of the week
  - Time of day
  - Accounts for when start time is later in the day than end time

**Schedule Implementation:**
- Overnight blocks: store as two separate time ranges or use 24-hour time comparison
- Device time changes: detect via system time vs. elapsed realtime comparison, treat manual changes as attempts to bypass - extend locks by the change amount
- Timezone changes: locks stored in UTC, displayed in local time
- DST: handled automatically by using UTC internally

#### Locks

**Lock Modes:**
- Unlocked
- Locked Until Date/Time
- Locked Until Timer Ends (functions like Date/Time but easier workflow)
- Locked Forever

**Lock Behavior:**
- Blocks can be enabled and disabled, but can't be disabled while locked
- While locked, blocks cannot be made less restrictive
- Edits that make blocks more restrictive are allowed (e.g., adding to block lists)
- Neutral edits are allowed (e.g., changing the title of the block)
- Edits that make blocks less restrictive are not allowed:
  - Removing from block list
  - Adding to allow list

**Lock Display:**
- Lock timers shown prominently: "Unlocks in 23h 45m"
- All times displayed in 24-hour format for clarity

**Architecture Note:**
All restrictions use time-based locks that cannot be bypassed from the UI. Configuration changes require waiting for the lock to expire.

#### App Blocking Mechanism

**Implementation:**
- Use `DevicePolicyManager.setPackagesSuspended()` to suspend blocked apps
- Check every 1-2 seconds for foreground app using `UsageStatsManager`
- If a blocked app comes to foreground, immediately suspend it and return user to home screen
- For apps with foreground services, suspend them anyway - this is about blocking distractions, not critical services

**User Feedback:**
- Show a simple toast: "This app is blocked"
- No elaborate interstitial that could be worked around

---

## App Installation Policy

### Three-Tier System

1. **Allowlist** – Apps that can be installed freely
2. **Cooldown** – Restricted categories require waiting period
3. **Blacklist** – Apps that can never be added to allowlist

### Default Configurations

**Allowlist (Pre-populated):**
- Phone
- Messages
- Camera
- Maps
- other default non-distracting android apps

**Blacklist (Pre-populated):**
- Instagram
- TikTok
- Twitter/X
- Reddit
- Facebook
- Snapchat
- YouTube app
- Common Reddit clients (Apollo, Sync, RIF, etc.)

### Adding Apps to Allowlist

**Process:**
1. User pastes Play Store link into the app
2. App fetches the Play Store page and checks:
   - Category
   - Presence of "About This Game" text
   - "Browser", "Firefox", "Duck" in title (additional keywords can be added in settings)

**Immediate approval if:**
- Category is NOT: Social, Entertainment, or Video Players & Editors
- Page does NOT contain "About This Game"
- Page does NOT contain blocked keywords in title

**Requires cooldown if:**
- Category IS one of the restricted categories, OR
- Page contains "About This Game" (catches games in any category)

**If parsing fails:** Default to requiring cooldown (fail safe)

### Cooldown Mechanism

**Timing:**
- Window: 3-6pm the following day
- Expiration: If the window is missed, the request expires and must be re-submitted
- Post-install: Apps that went through cooldown become "managed" and can be scheduled/blocked on a time basis

**Queue System:**
- Multiple apps can be in cooldown, stored in a queue
- Main screen shows "Pending Approvals" section with countdown to 3pm tomorrow

**Notifications:**
- At 3:00pm, show notification: "App installation window open for 3 hours"

**Missed Window:**
- Request deleted, must re-submit
- This creates high friction as intended

### Managed Apps After Cooldown

**Configuration Options:**
- Can add them to any existing block or create new block for them
- Can configure schedules (e.g., "30 min per day between 7-9pm")

**Uninstallation:**
- Allowed, but requires 24-hour cooldown after request (same friction as installation)

**Statistics:**
- No usage statistics initially - just on/off blocking

### Blacklist

- Permanent deny list of package names
- Can never be added to allowlist regardless of category
- Use for known time sinks discovered over time
- Pre-populate with obvious problems (Reddit clients, TikTok, Twitter/X, etc.)

### App Updates

- Handled automatically by Play Store
- If an updated app becomes problematic, add it to the blacklist

### APK Sideloading

- Block installation from unknown sources via Device Owner API

### Play Store Control

**Access Management:**
- Play Store kept suspended by default via device owner APIs
- Unsuspended temporarily (with short timeout) only for approved installations
- Launch directly to specific app's page via intent
- Re-suspend immediately after installation detected or timeout expires

**Temporary Access Details:**
- Timeout: 5 minutes
- Installation detection: `PackageManager.PackageInstallObserver` or broadcast receiver for `ACTION_PACKAGE_ADDED`
- User cancellation: timeout expires, Play Store re-suspended
- Multiple apps: one at a time only, must complete or timeout before next

---

## Technical Implementation Details

### Play Store Page Parsing

**Use Jsoup for HTML parsing**

#### Non-Game Apps

**Play Store URL format:**
```
https://play.google.com/store/apps/details?id=<package_name>&hl=en-US
```

Package name extracted from `id=` parameter.

**Category Detection:**

Look for:
- `itemprop="genre"` with `aria-label="<category>"`
- OR `href="/store/apps/category/<category>"`

**Categories to block (require cooldown):**
- Social
- Entertainment
- Video Players & Editors

**Example HTML patterns:**

Social category:
```html
<div class="VfPpkd-dgl2Hf-ppHlrf-sM5MNb" data-is-touch-wrapper="true">
  <div class="VfPpkd-LgbsSe..." itemprop="genre">
    <span jsname="V67aGc" class="VfPpkd-vQzf8d" aria-hidden="true">Social</span>
    <a jsname="hSRGPd" class="..." href="/store/apps/category/SOCIAL" aria-label="Social"></a>
  </div>
</div>
```

Entertainment category:
```html
<div class="VfPpkd-dgl2Hf-ppHlrf-sM5MNb" data-is-touch-wrapper="true">
  <div class="VfPpkd-LgbsSe..." itemprop="genre">
    <span jsname="V67aGc" class="VfPpkd-vQzf8d" aria-hidden="true">Entertainment</span>
    <a jsname="hSRGPd" class="..." href="/store/apps/category/ENTERTAINMENT" aria-label="Entertainment"></a>
  </div>
</div>
```

Video Players & Editors category:
```html
<div class="VfPpkd-dgl2Hf-ppHlrf-sM5MNb" data-is-touch-wrapper="true">
  <div class="VfPpkd-LgbsSe..." itemprop="genre">
    <span jsname="V67aGc" class="VfPpkd-vQzf8d" aria-hidden="true">Video Players &amp; Editors</span>
    <a jsname="hSRGPd" class="..." href="/store/apps/category/VIDEO_PLAYERS" aria-label="Video Players &amp; Editors"></a>
  </div>
</div>
```

#### Games

**Detection:**

Look for "About this game" heading:
```html
<h2 class="XfZNbf">About this game</h2>
```

#### Browsers

**Detection:**

Look for blocked keywords in `<h1>` tag:
- "Browser"
- "Firefox"
- "Duck"

**Example:**
```html
<h1><span class="AfwdI" itemprop="name">Firefox Fast &amp; Private Browser</span></h1>
```

### Play Store Parsing Fallback

**Error Handling:**
- If HTML structure changes and parsing fails: default to requiring cooldown (fail-safe approach)
- Network failure: show error, let user retry, but don't allow installation
- Keep a local cache of previously checked apps with their categories/status

---

## Data and Persistence

**Storage:**
- SQLite database for everything (blocks, locks, allowlist, blacklist, cooldowns)
- Disable backups for the database via manifest flag to prevent restore exploits
- Pending cooldowns stored in the same database

**Uninstall Behavior:**
- If app is uninstalled while device owner: phone needs factory reset (that's the Android design)

---

## Security and Restrictions

### Security Loopholes - Blocked

**Protections:**
- Block USB debugging immediately after device owner is set (via `DevicePolicyManager`)
- Safe mode: Device owner restrictions persist in safe mode
- Block accessibility service installations for apps not on allowlist
- Block split screen and picture-in-picture via Device Owner APIs
- Block adding secondary users via `DevicePolicyManager.addUserRestriction()`
- Block developer options access

### Emergency Override

**Escape Hatch:**
- "Locked Forever" means until manually removed - the escape hatch is the only way out
- Location: buried in settings
- Requires typing a long passphrase like "I understand this will require factory reset"
- Calls `clearDeviceOwnerApp()` which allows uninstalling the app
- After that, factory reset is needed to regain normal control

**Philosophy:**
- For true emergencies: you can still use phone, browser, messaging - this doesn't brick the device
- The app blocks distractions, not critical functionality

### System App Interactions

**Always Accessible:**
- Phone
- Messages
- Camera
- Clock
- Calculator
- Maps

**Settings Access:**
- Settings app accessible
- Restrict certain sections via Device Owner:
  - Developer Options
  - Accounts
  - Apps & Notifications details

**System UI:**
- Notification shade: accessible
- Quick settings: accessible but some tiles restricted (like airplane mode if desired)

---

## UI/UX Structure

### Main Screen
- List of blocks with on/off toggles (disabled if locked)
- Lock status icons
- Tab or section for "Pending Approvals" with countdown to next window

### Block Edit Screen
- Tap a block to access
- Shows apps/websites blocked
- Schedule settings
- Lock controls

### Blocked Content Feedback
- Toast notification only: "This app is blocked"
- Minimal interruption

### Settings
- Blacklist management
- Browser keyword list
- Escape hatch option

---

## Development Workflow

### Test Device

Use a dedicated device for development, not daily driver. Initial development will be in Android emulator before moving to a Pixel 9a.

### Dev Mode

The app should have a dev mode while in development so that it is easy to update the app without resetting the phone.

### Key Points

- **Updates don't require factory reset** – as long as package name and signing key stay the same, device owner status persists
- **Set device owner via ADB** for faster iteration:
  ```bash
  adb shell dpm set-device-owner com.yourpackage/.YourDeviceAdminReceiver
  ```
- **Keep signing key consistent** – generate early, use for all builds
- **Include escape hatch** – a method to call `clearDeviceOwnerApp()` for graceful removal when needed

---

## Out of Scope

These are intentionally not supported, with laptop as fallback:

- Google OAuth / "Sign in with Google"
- Complex web applications
- Full-featured browsing
- Password manager autofill (copy-paste from 1Password is acceptable)

---

## Project Summary

This app creates a minimal, focused Android device through Device Owner APIs and a custom browser. The three-tier app installation system (allowlist/cooldown/blacklist) combined with time-locked blocks creates significant friction for accessing distracting content while maintaining utility for essential tasks.

Key implementation technologies:
- Device Owner APIs (`DevicePolicyManager`)
- WebView with custom URL interception
- Google ML Kit for QR scanning
- Jsoup for HTML parsing
- SQLite for data persistence
- UsageStatsManager for app monitoring
