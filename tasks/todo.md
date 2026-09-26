# Android app — port of the "Recess" (Grok workspace) parent dashboard

Source: `grok-workspace.zip` — a TanStack Start web app (React, zustand, localStorage) that turns
Google Classroom work into Family Link bonus screen time. All real logic lives in
`src/lib/engine.ts` (pure functions) + `seed.ts` + `format.ts`; the server/auth/DB scaffolding
is unused by the product.

## Decision
Native **Kotlin + Jetpack Compose** app (not a WebView wrapper): the web app is SSR-only with no
static SPA build, and the engine is small, pure and well-tested — a clean port is simpler and
gives a real Android UX (offline, persisted state, Material components, back navigation).

## Plan
- [x] Gradle project: `:core` (pure Kotlin/JVM — models, engine, seed, format) + `:app` (Compose)
- [x] Port `types.ts` → `Models.kt` (kotlinx.serialization)
- [x] Port `engine.ts` → `Engine.kt` (same semantics; injectable `now` + zone)
- [x] Port `seed.ts`, `format.ts`
- [x] Port `engine.test.ts` → JUnit (all 14 cases) + format tests
- [ ] Persistence: JSON file repository (replaces zustand `persist`/localStorage)
- [ ] ViewModel exposing `StateFlow<FamilyState>` + one-shot snackbar messages (replaces toasts)
- [ ] Theme: original palette (sage/cream), Fraunces + Figtree fonts (OFL, bundled)
- [ ] Screens: Home, Classroom, Rules, Log, Settings, Child detail; bottom navigation
- [ ] Components: time ring, cap meter, week chart, task row, grant queue, earnable panel,
      device preview, supervised apps, give-time dialog, provision sheet, add-child dialog
- [ ] CI: GitHub Actions — unit tests + debug APK artifact
- [ ] Verify: `:core:test`, `:app:assembleDebug`, lint; render check
- [ ] README with build/run instructions

## Status (paused)
- `:core` done: models, engine, seed, format, JSON codec; 23 JUnit tests pass (14 ported + 9 new).
- `:app` has only the Gradle config + bundled fonts; the screens aren't written yet.
- Local APK builds are blocked (dl.google.com is denied by the sandbox network policy); the full build is meant to be checked in GitHub Actions.
- Paused: the user's second upload already includes a WebView-wrapped `Recess.apk`. Waiting for the user to decide whether to finish the native port.

## Review
_(filled in at the end)_
