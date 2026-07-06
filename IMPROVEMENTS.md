# Portfolio Improvements — Remaining Work

Items identified during the portfolio review (July 2026) that are not yet done.
Completed in that review: unit test suite (63 tests), GitHub Actions CI, MIT license,
README rewrite (removed stale browser/QR content, added engineering highlights +
architecture diagram), CI-friendly signing fallback, dependency catalog cleanup.

## Needs the repo owner (can't be done from code)

- [ ] **Rename the GitHub repo** from `AndroidPhoneManager` to something matching the
      project (e.g. `self-control-android`). GitHub auto-redirects the old URL.
      After renaming, update the CI badge URL at the top of `README.md`.
- [ ] **Add screenshots / demo GIF to the README.** This is the highest-impact remaining
      item: reviewers can't run a Device Owner app, so visuals are the only way they see
      it work. Capture: main block list, block edit screen, schedule editor, lock dialog,
      and what a suspended app looks like when opened. Replace the
      `<!-- TODO: add screenshots -->` placeholder in `README.md`.
      (Android Studio's screen recorder or `adb emu screenrecord` both work.)
- [ ] **Push and confirm CI goes green**, then the badge renders on the repo page.

## Code quality

- [ ] **Split the largest composables.** `BlockEditScreen.kt` (~1,080 lines) and
      `SettingsScreen.kt` (~740 lines) should be broken into focused components under
      `ui/components/`. Good interview talking point about refactoring discipline.
- [ ] **Add static analysis** (detekt or ktlint) and wire it into the CI workflow.
- [ ] **Fix the deprecation warning** in `SettingsScreen.kt:731`
      (`clearDeviceOwnerApp` is deprecated).
- [ ] **Inject a `Clock`/time source into `LockManager`** instead of calling
      `Instant.now()` directly, so expiry edge cases can be tested deterministically
      (ScheduleManager already takes `now` as a parameter — mirror that pattern).

## Testing

- [ ] **Room DAO tests** (instrumented or Robolectric) for the critical
      `getBlockedPackageNames()` query and cascade deletes.
- [ ] **Tests for `AppInstallationManager`** (cooldown window math: 24-hour wait,
      3–6 PM approval window, expiration).
- [ ] **Tests for `UrlParser` / website rule matching.**

## Features / completeness

- [ ] **Finish Phase 7** (app installation control) — see CLAUDE.md implementation status.
- [ ] **Phase 8: security hardening** — e.g. protect Settings with a lock, harden against
      `adb` bypasses where possible.
- [ ] **Consider enabling `isMinifyEnabled = true`** for release builds (R8), with keep
      rules for Room/Hilt/reflection.

## Process (going forward, not retroactive)

- [ ] Write commit messages in imperative style describing the change
      ("Add overnight schedule support"), not status updates ("working on fixing X").
- [ ] Consider opening PRs against `main` (even solo) so CI gates changes and the
      history shows review discipline.
