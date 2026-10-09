# Kartka: notes for coding agents

A local-first markdown notes app for Android (Kotlin, Jetpack Compose, Material 3 Expressive). There is no
database: the app reads and writes a folder of `.md` files that the user picks, usually synced with Syncthing.
Package `com.s1nn3rv2.kartka`, minSdk 31, a single `:app` module.

## Rules that protect the user's notes

The notes folder is the only source of truth, and every write is synced to the user's other devices. So:

- **Never write a note unless its text changed.** Opening, scrolling, folding, pinning or reading must not touch
  the file. `NoteSession.saveLocked` returns early when the text equals what was loaded or last saved.
- **Writes are atomic and flushed** (`NotesRepository.write`: temp file, `fd.sync()`, then an atomic move). Keep it
  that way.
- **Keep the file's format.** The byte order mark and line endings round-trip through `TextFormat`
  (`storage/NoteText.kt`). Files that aren't valid UTF-8 open read-only and are never written.
- **Changes from other devices:** `NoteSession` compares the file's text, not only its modified time, before
  calling something a conflict (phone storage can report a stale time right after a save). With unsaved edits it
  shows a banner instead of overwriting; leaving with a conflict open saves a "(my edits)" copy.
- **App-only state never goes into the notes folder.** Pins, folds, reading positions and settings live in private
  `SharedPreferences` files: `settings`, `pins`, `folds`, `positions`, `launch`. When a note is renamed or moved,
  `Notes.pathChanged` carries its entries over.
- Deleting moves a note to `.trash/` inside the notes folder (purged after 30 days); scratch notes live in
  `.scratch/`. Folders starting with `.` and the `attachments/` folder are hidden from lists.
- **Never use the user's real notes** in tests, screenshots or commits. Make up demo content (see "Emulator").

## Build and test

Use the JDK bundled with Android Studio (on Linux usually `JAVA_HOME=/opt/android-studio/jbr`). Gradle downloads the
JVM it runs on by itself (`gradle/gradle-daemon-jvm.properties`).

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest   # build and run the unit tests
./gradlew :app:assembleRelease                         # release APK (R8 on)
./install-phone.sh                                     # release build onto a USB phone, compiled ahead of time
```

- Unit tests are plain JVM tests in `app/src/test` (no device needed) covering parsing, search, previews, edits
  and storage. Add a test with every logic change, and say in a comment why the behavior matters.
- **Measure performance on a release build**, never a debug build (debug is 3 to 6 times slower). The release
  build is `profileable`, so `simpleperf` and Android Studio's profiler work on it.
- `install-phone.sh` signs with this computer's **debug key**. Builds from GitHub releases use the release key,
  and Android refuses to update one with the other ("conflict"). Uninstall first when switching. The script installs
  only for the current user; a plain `adb install` also installs into Private space and other profiles, where a
  leftover copy later blocks installs.

## Code map (`app/src/main/java/com/s1nn3rv2/kartka/`)

- `Notes.kt`: app-wide state. Scanning, the in-memory search index, folder watching, refresh, pins, folds,
  reading positions, link resolution. `Settings.kt`: preferences as flows (`Pref`), app lock, app name aliases.
- `storage/`: `NotesRepository` (all file I/O; every call blocks, run it on `Dispatchers.IO`), `NoteText` (encoding,
  file name rules), `FolderTree`.
- `index/`: `Search` (accent-free matching, `#tag` and `tag:` filters, snippets), `Preview` (card previews, sorting
  and grouping, word counts).
- `markdown/`: one commonmark-java parser setup in `Md.kt` (with the Logseq "dedent" that maps offsets back),
  custom inline parsing for wiki links, tags, highlights (`Inlines.kt`) and math (`Math.kt`).
  `Blocks.kt` builds View-mode blocks and folding; `StyleBuilder.kt` builds Edit-mode color ranges;
  `Edits.kt` holds the toolbar operations and undo. All pure Kotlin, so all unit-testable.
- `editor/`: Edit mode is a native `EditText` (`MarkdownEditText`) in an `AndroidView`, not Compose text.
- `ui/`: Compose screens. `SlideStack.kt` is the hand-rolled back stack with predictive back. `note/NoteScreen.kt`
  switches between View mode (`MarkdownView`, a `LazyColumn`) and Edit mode, and `NoteSession` owns
  load, save, rename, move and conflicts.

## Things that were tried and why the code looks the way it does

- **Navigation 3 was removed** because it played the wrong animation on quick predictive back swipes. Keep
  `SlideStack`.
- **Edit mode performance:** keep `FastEditable` and `PassiveSpan` (they skip per-span work while typing) and don't
  use `ReplacementSpan` (Android redraws every block containing one on every frame). Restyling is windowed with
  `changedWindow`. The hidden editor is built only once a note has been still for a moment (`prepareEditor`),
  because laying out a long note takes about 60 ms on the main thread.
- **View and Edit mode used to be one live-preview editor;** it was too slow and buggy on a real phone, so they
  were split.
- **commonmark callbacks** (`BlockStart.none()`, `BlockContinue.none()`, `ParsedInline.none()`) return Java
  `null`, so the Kotlin overrides must have nullable return types.
- **Material 3 is the 1.5 beta** because the Expressive components only exist there. Several APIs need
  `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. The stock `ModalWideNavigationRail` opens as a separate
  window and would sit above the next screen, so the sidebar is built from the rail's parts in `HomeScreen.kt`.
- **Colors come only from `MaterialTheme.colorScheme`**: no hardcoded colors. The theme sets
  `background = surface` because some palettes make them differ, which showed as a band behind the top bars.
  Snackbars go through `ui/Snackbars.kt` (`AppSnackbarHost`, `showUndo`).
- **jlatexmath loads classes by name:** `app/proguard-rules.pro` keeps them for release builds. It is GPL v2
  (the app's own code is MIT).
- **Launcher names:** `MainActivity` isn't exported; two activity aliases (`.Kartka`, `.Notes`) are, and Settings
  enables one. Launcher shortcuts and Quick Settings tiles go through `ShortcutActivity`, which adds a one-time id
  so Android replaying an old shortcut intent after an update can't create a second note.

## Emulator

- Start the app with `adb shell monkey -p com.s1nn3rv2.kartka -c android.intent.category.LAUNCHER 1`; there is no
  fixed launch component because of the aliases.
- For screenshots or tests, put made-up notes in a separate folder (for example `/storage/emulated/0/DemoNotes`)
  and point the app there by editing `shared_prefs/settings.xml` through `adb shell run-as com.s1nn3rv2.kartka`.
  Back up the app's `shared_prefs` first and restore them afterwards. The system UI demo mode gives a clean
  status bar.
- `adb shell input text` can't type `#`; use `input keyevent POUND`. A Google sign-in popup sometimes takes over
  after a cold boot: `adb shell am force-stop com.google.android.gms`. If a screen lock PIN is set, storage stays
  locked after a reboot until it's unlocked.

## Releases

- Pushing a tag `vX.Y.Z` runs `.github/workflows/build.yml`: tests, a release APK signed with the key from the
  repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), and a GitHub release
  with the APK and its `.sha256`. The version comes from the tag (`v1.2.3` → versionName 1.2.3, versionCode 10203),
  which Obtainium relies on.
- The signing certificate fingerprint in `README.md` belongs to the key, so it never changes between releases.
- Don't push, tag or release unless the user asks.

## Conventions

- Commit messages: `type: short description` with type one of feat, fix, chore, refactor, docs, test, style, perf,
  ci. No AI co-author or "generated with" lines.
- Write comments and docs in plain English that explains why, matching the surrounding code. Keep changes minimal.
