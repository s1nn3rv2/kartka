# Kartka

A private, local-first markdown notes app for Android. There is no database and no account: Kartka works directly on
a folder of `.md` files that you choose, keeps its folder structure, and never changes a file you didn't edit. Sync
the folder with [Syncthing](https://syncthing.net/) (or anything else) and the same notes work in any editor on
your computer.

The app has no internet permission, no analytics and no crash reporting.

## Features

**Notes and folders**
- Home shows your latest notes (or all notes by name), with pinned notes first. Pins stay inside the app; nothing is
  written to your notes folder.
- A sidebar lists your folders. New notes go to the folder you're in, or to a default folder you choose.
- Search titles and text, without needing accents (`skladniki` finds `Składniki`). Type `#tag` to find tagged notes.
- Hold a note to select several and pin or delete them; swipe a note right to pin it, left to delete it.
- Deleted notes go to a `.trash` folder inside your notes folder for 30 days, with Undo.
- Scratch notes for quick throwaway text, kept in `.scratch` and removed after 30 days unless you keep them.
- Quick capture: launcher shortcuts, Quick Settings tiles, and sharing text from other apps.

**Reading and editing**
- View mode renders the markdown; Edit mode shows the raw text with syntax colors and a formatting toolbar.
- Headings, lists, task checkboxes, tables, quotes, code blocks with syntax colors, footnotes, LaTeX math,
  `[[wiki links]]` with backlinks and name suggestions, `#tags`, `==highlights==` and Obsidian-style callouts.
- Fold headings and bullets (remembered per note), jump to a heading from the outline, and indent, outdent or
  move bullets with their children. Logseq-style tab-indented outlines work as expected.
- Add photos from your phone: they're copied to an `attachments` folder and linked so desktop editors show them too.
- If a note changes on another device while you're editing it, Kartka asks which version to keep instead of
  overwriting either one.
- Files keep their exact format (line endings, byte order mark); saves are atomic, so a crash never leaves a
  half-written note.

**Privacy and looks**
- Optional fingerprint or screen lock, and an option to hide the app's contents in Recents and screenshots.
- Material 3 Expressive design with Material You colors (or the standard palette), light and dark themes.
- The launcher name can be "Kartka" or "Notes".

## Install

There are no prebuilt releases yet. To build it yourself you need the Android SDK (easiest with Android Studio).
Gradle downloads the Java version it needs on its own:

```sh
./gradlew :app:assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. The release build is signed with your local debug key; use your
own signing key if you distribute it.

On first start, grant "All files access" and pick your notes folder. On GrapheneOS you can use Storage Scopes to give
Kartka access to only that folder.

Requires Android 12 or newer.

## Built with

- Kotlin and Jetpack Compose with Material 3 Expressive
- [commonmark-java](https://github.com/commonmark/commonmark-java) for parsing markdown (BSD 2-Clause)
- [jlatexmath-android](https://github.com/noties/jlatexmath-android) for drawing math (GPL version 2)

## License

Kartka's own code is under the [MIT License](LICENSE). Note that it includes jlatexmath-android, which is licensed
under the GPL version 2, so a built APK that contains it has to be shared under the GPL's terms (including making the
source available, which this repository does).
