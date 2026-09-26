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
- [ ] Model additions: `Child.classroomEmail`, nullable `ClassroomTask.dueAt`,
      `SupervisedApp.packageName`, `FamilySettings.deviceChildId`/`classroomAccount`
- [ ] Classroom API DTOs + `ClassroomImport`: map courses/coursework/submissions → tasks;
      first sync is a baseline (no retroactive grants); later transitions
      (assigned → turned in / returned+graded) run the existing rules engine
- [ ] `UsageImport`: apply an on-device usage snapshot (today + 7-day history + per-app) to a child,
      keeping parent-set app limits/blocks
- [ ] `Seed.empty()` for a real (non-demo) family
- [ ] Tests for all of the above

### :app
- [ ] Repository: JSON file persistence, `StateFlow<FamilyState>`
- [ ] Google authorization (Play services `AuthorizationClient`, read-only Classroom scopes)
- [ ] Classroom REST client (HttpURLConnection + kotlinx.serialization, paging)
- [ ] Usage reader (UsageStatsManager events → minutes per app per day)
- [ ] ViewModel: actions + snackbar messages; real sync when signed in & linked, demo replay otherwise
- [ ] Theme (original palette, Fraunces + Figtree), bottom navigation
- [ ] Screens: Home, Classroom, Rules, Log, Settings (Google account, child links, this-device
      usage), Child detail; dialogs: give time, provision, add child, time pickers
- [ ] Launcher icon, manifest (INTERNET, PACKAGE_USAGE_STATS, launcher `<queries>`)
- [ ] Committed debug keystore → stable SHA-1 for the OAuth client
- [ ] GitHub Actions: core tests + assembleDebug, upload APK
- [ ] README: Google Cloud setup (enable Classroom API, consent screen, Android OAuth client)

### Verify
- [ ] `:core:test` locally
- [ ] CI green on the branch (full Android build can't run here — dl.google.com is blocked)

## Review
_(filled in at the end)_
