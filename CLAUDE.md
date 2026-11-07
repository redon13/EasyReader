# EasyReader – instructions for Claude

EasyReader is a private Android client for the owner's Calibre library. Read `docs/SPEC.md` (what to build), `docs/DESIGN.md` (how it looks) and `docs/SERVER-SETUP.md` (the server it talks to) before starting any work.

## Working with the owner

- The owner is not a programmer. Explain things in plain words, avoid jargon, and keep messages short.
- **Before creating, editing or deleting any file, describe the change and get explicit confirmation.** A stage plan that lists the files, once approved, covers those files.
- Ask clarifying questions when anything is unclear, like a new employee making sure they understand what's expected, rather than guessing.
- The owner tests on a Samsung Galaxy S23 Ultra and a Galaxy Tab S9 FE, installing from GitHub Releases. They cannot build locally.
- British English in all user-facing text.

## Build order

Work one stage at a time. Each stage ends with a signed `.apk` on GitHub Releases that the owner installs and tests; wait for their go-ahead before starting the next stage.

1. Project skeleton, GitHub Actions build + signed Releases, connect to the server, log in, show the library as covers.
2. Browse, search, filters, sort and book details.
3. Reader (EPUB + PDF, three backgrounds), offline downloads, reading position sync.
4. Shelves, favourites, finished status, smart shelves.
5. Uploads, settings (incl. Sync Now), tablet two-pane layout, 7-day clean-up, polish.

Track progress in the Status section of `README.md`.

## Key decisions (do not change without asking)

- Android only, minimum API 33. Kotlin, Jetpack Compose, Material 3, Room, WorkManager, Readium Kotlin Toolkit.
- Talk to Calibre's content server using the same JSON endpoints its web UI uses (not just OPDS). Keep all server code in one self-contained module/package, since these endpoints are undocumented and may change.
- Shared state lives in four Calibre custom columns: `#er_shelves`, `#er_favourite`, `#er_finished`, `#er_position` (JSON: locator, progress %, timestamp, device). The app should detect missing columns and tell the owner which to add.
- Reading activity syncs instantly (queued offline, most recent timestamp wins). Library details sync every 10 minutes and on Sync Now / pull-to-refresh.
- Books auto-download on open; finished downloads are deleted after 7 days.
- Book details are view only. Reader background options apply to the reader only. Accent colour is fixed amber.
- Single user. No credentials, server addresses or signing keys in the repo (the repo is public): the signing keystore and its passwords go in GitHub Actions secrets, and the server details are entered in the app.
- Out of scope for v1: bookmarks/highlights/notes, dictionary, read-aloud, metadata editing, comics reader, other platforms.

## Builds

- GitHub Actions builds on every push; tagging a version (e.g. `v0.1.0`) publishes a signed release `.apk` to Releases.
- Keep `versionCode` increasing so updates install over the top.
- The owner will need step-by-step help creating the signing key and adding the GitHub secrets the first time.
