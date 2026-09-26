# Recess (Android)

Native Android app (Kotlin + Jetpack Compose) that turns Google Classroom work into bonus screen
time. It's a port of the Recess web app, and adds Google sign-in for real Classroom data plus real
on-device screen time.

## What it can and can't do

| Data | Source | Notes |
| --- | --- | --- |
| Courses, coursework, submissions, grades | **Google Classroom API** (read-only) | Sign in with a **teacher** (their own classes) or **Google Workspace admin** (any class) account. Google gives parent/guardian accounts no API access to coursework. |
| Screen time per app | **Android Usage Access** on the phone Recess runs on | Install Recess on the child's phone, pick the child under *Settings → This phone's screen time*, and allow Usage access. |
| Family Link limits / bonus time | — | **Family Link has no public API.** Recess works out the minutes a child has earned; you add them in the Family Link app. App limits and blocks in Recess are a record, not enforcement. |

Without a Google account connected, the app runs on the demo family and **Sync** replays sample submissions.

## Set up Google sign-in (once)

1. In [Google Cloud Console](https://console.cloud.google.com/), create or pick a project.
2. **APIs & Services → Library →** enable **Google Classroom API**.
3. **OAuth consent screen:** User type *External* (or *Internal* for a Workspace domain). Add the
   scopes below. While the app is in *Testing*, add every Google account that will sign in as a
   **test user**.
   - `.../auth/classroom.courses.readonly`
   - `.../auth/classroom.coursework.students.readonly`
   - `.../auth/classroom.rosters.readonly`
   - `.../auth/classroom.profile.emails`
4. **Credentials → Create credentials → OAuth client ID → Android:**
   - Package name: `app.recess.famlink`
   - SHA-1: `FD:FD:63:72:3C:E4:C5:04:5E:22:07:28:A1:77:A1:2E:31:B6:55:97`

   That SHA-1 belongs to `app/debug.keystore`, which is committed on purpose so every build (local or CI) is signed
   the same way. No client ID goes in the code: Play services matches the app by package name + SHA-1.
5. School accounts: a Workspace admin may need to allow the app under
   *Admin console → Security → API controls → App access control*.

In the app: **Settings → Sign in with Google**, then **Link** each child to their school email and tap **Sync Classroom**.
The first sync for each child records their existing work without awarding bonus time. After
that, each new turn-in or high grade runs your rules, once.

## Build

Requirements: JDK 17+ and the Android SDK (API 35).

```bash
./gradlew :core:test          # rules engine + importers (pure Kotlin, no Android needed)
./gradlew :app:assembleDebug  # APK at app/build/outputs/apk/debug/app-debug.apk
```

Every push runs the same build in GitHub Actions (`.github/workflows/android.yml`) and uploads the APK as the
`Recess-apk` artifact.

## Install on a phone

Download the APK onto the phone, open it, and allow *Install unknown apps* for the app you
opened it from when Android asks. Needs Android 8.0 or newer.

## Layout

- `core/`: pure Kotlin: models, the bonus rules engine (ported from the web app's `engine.ts`),
  Classroom and usage importers, demo seed, formatting. Unit-tested.
- `app/`: the Android app: Compose UI, JSON persistence, Google authorization, Classroom REST client, usage reader.
