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

- [x] **Split the largest composables.** `BlockEditScreen.kt` (1,078 → 372 lines) and
      `SettingsScreen.kt` (738 → 258 lines) broken into focused components under
      `ui/components/` (rule cards, schedule card, lock status card, pickers/dialogs,
      and a reusable `LockDialogState` + `LockSettingDialogs` pair that deduplicates
      the four lock/forever/extend dialog trios in Settings).
- [x] **Add static analysis** — detekt with a Compose-aware config
      (`config/detekt/detekt.yml`), wired into CI before the test step. Pre-existing
      broad exception handling in the service/workers is captured in
      `app/detekt-baseline.xml` to burn down over time; new code must be clean.
- [x] **Fix the deprecation warning** for `clearDeviceOwnerApp` — suppressed with a
      rationale comment: the suggested replacement (`wipeData()`/factory reset) is
      wrong for this feature, and no non-destructive replacement exists.
- [x] **Inject a `Clock`/time source into `LockManager`** (provided via Hilt
      `ClockModule`); `LockManagerTest` now uses `Clock.fixed(...)` with deterministic
      expiry edge-case tests (expiry exactly at now, 1 ms before expiry).

## Testing

- [x] **Room DAO tests** (instrumented or Robolectric) for the critical
      `getBlockedPackageNames()` query and cascade deletes.
      (`BlockDaoTest`, 14 Robolectric tests with an in-memory Room DB.)
- [x] **Tests for `AppInstallationManager`** (cooldown window math: 24-hour wait,
      3–6 PM approval window, expiration). (`AppInstallationManagerTest`, 26 tests;
      `Clock` injected like `LockManager` for deterministic time.)
- [x] **Tests for `UrlParser` / website rule matching.**
      (`ParsedUrlTest` + `WebsiteRuleChromeFormatTest`, 32 tests.)

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
