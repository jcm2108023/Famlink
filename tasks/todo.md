# Android app — native port of "Recess" + Google sign-in

## Decisions (from the user, 2026-09-26)
- **Native Kotlin + Jetpack Compose** app (Google blocks OAuth inside WebViews, so the WebView APK
  can't do Google sign-in).
- **Teacher / Workspace-admin account signs in.** The Classroom API gives guardians no access to
  coursework, but a teacher (for their own classes) or a domain admin (for any class) can read a
  student's courses, coursework and submissions. Each child is linked by their school email.
- **Family Link has no public API.** Recess stays the ledger of earned minutes (applied by hand in
  Family Link), and adds **real on-device usage** via Android Usage Access when Recess runs on the
  child's phone.

## Review of the WebView APK (2nd upload)
- WebView wrapper around a static export of the web app; renders fine and works offline.
- Classroom "Connected" badge and Family Link "2 devices" are hard-coded — nothing is connected.
- "Sync Classroom" replays demo data; "Send to device" only writes to local storage.
- Keystore + passwords committed in plain text in `android/app/build.gradle`.
- React hydration error #418 at startup (server-rendered date baked into `index.html`).
- Fonts load from Google Fonts at runtime, so offline it falls back to system fonts.

## Plan
### :core (pure Kotlin, unit-tested locally)
- [x] Models, engine, seed, format, JSON codec (+23 tests)
- [x] Model additions: `Child.classroomEmail`, nullable `ClassroomTask.dueAt`,
      `SupervisedApp.packageName`, `FamilySettings.deviceChildId`/`classroomAccount`
- [x] Classroom API DTOs + `ClassroomImport`: map courses/coursework/submissions → tasks;
      first sync is a baseline (no retroactive grants); later transitions
      (assigned → turned in / returned+graded) run the existing rules engine
- [x] `UsageImport`: apply an on-device usage snapshot (today + 7-day history + per-app) to a child,
      keeping parent-set app limits/blocks
- [x] `Seed.empty()` for a real (non-demo) family
- [x] Tests for all of the above

### :app
- [x] Repository: JSON file persistence, `StateFlow<FamilyState>`
- [x] Google authorization (Play services `AuthorizationClient`, read-only Classroom scopes)
- [x] Classroom REST client (HttpURLConnection + kotlinx.serialization, paging)
- [x] Usage reader (UsageStatsManager events → minutes per app per day)
- [x] ViewModel: actions + snackbar messages; real sync when signed in & linked, demo replay otherwise
- [x] Theme (original palette, Fraunces + Figtree), bottom navigation
- [x] Screens: Home, Classroom, Rules, Log, Settings (Google account, child links, this-device
      usage), Child detail; dialogs: give time, provision, add child, time pickers
- [x] Launcher icon, manifest (INTERNET, PACKAGE_USAGE_STATS, launcher `<queries>`)
- [x] Committed debug keystore → stable SHA-1 for the OAuth client
- [x] GitHub Actions: core tests + assembleDebug, upload APK
- [x] README: Google Cloud setup (enable Classroom API, consent screen, Android OAuth client)

### Verify
- [x] `:core:test` locally
- [x] CI green on the branch (full Android build can't run here — dl.google.com is blocked)

## Review
**Verified**
- `:core:test`, 35 tests: the original engine cases, the Classroom importer (baseline, turn-in,
  grades once, reclaim/resubmit, history window, UTC due dates, API JSON), the usage importer, formatting.
- CI green on `ead8592`: core tests, a Robolectric UI smoke test that launches `MainActivity` and
  drives every tab, child detail + give-time dialog, and demo sync; lint; `assembleDebug`.

**Not verified here** (no emulator; the sandbox can't download the APK or reach Google Maven)
- Real Google sign-in and Classroom API calls need the user's OAuth client + a teacher/admin account.
- UsageStatsManager reading on a physical phone.

**Notes**
- The package id (`app.recess.famlink`) differs from the WebView APK (`app.recess.family`), so both can be installed side by side.
- App-limit and block toggles are a ledger only; Family Link can't be controlled by third-party apps.
