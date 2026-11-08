# EasyReader – App Spec

Agreed 7 November 2026. The living version of this spec is the "EasyReader – App Spec" doc in the owner's Book Shelf project on claude.ai; this file is a copy for the repo.

## Overview

EasyReader is a private Android app that connects to my Calibre library on my home server, shows everything Calibre knows about each book, and lets me read, download and upload books from anywhere.

- **User:** just me, one Calibre login.
- **Devices:** Android only — Samsung Galaxy S23 Ultra (phone) and Galaxy Tab S9 FE (tablet, main device).
- **Server:** Calibre on the Windows 11 PC that also runs Plex, reached remotely through Tailscale.
- **Library:** a few hundred books, mostly EPUB and PDF.
- **Code and builds:** this public repo; GitHub Actions builds the installable app automatically.
- **Who builds it:** Claude writes all the code (in Claude Code, one stage at a time); the owner approves each stage.

## Look and feel

| Area | Decision |
| --- | --- |
| App colours | Dark navy background, amber accent (fixed, not user-selectable) |
| Reader background | White, light cappuccino or dark — chosen in Settings, reader only for now |
| Layout on the phone | Single column |
| Layout on the tablet (landscape) | Book list and book details side by side |
| Panels | Filters, shelves and sort open as pull-up panels from the bottom |
| App name | EasyReader |
| Icon | Option A: a navy open book on an amber rounded square (`assets/icon.svg`) |

Details in [DESIGN.md](DESIGN.md).

## Screens and features

1. **Home**
    - "Continue reading" card at the top with the current book's cover and progress.
    - Filters button, sort button and grid/list toggle; search bar across the whole library.
    - Horizontal rows: Continue reading, Recently added, Favourites, Unread, All books.
    - Optional progress bar on each cover; heart icon to favourite.
    - Sync Now in the menu; pull down to sync.
2. **Filters and shelves panel** — three tabs:
    - **Filters:** Authors, Series, Languages, Tags, Publishers, Formats, each with a book count.
    - **My shelves:** Play Books-style shelves created and filled from the app; saved in Calibre so every device sees them.
    - **Smart shelves:** Calibre virtual libraries and saved searches, shown automatically (view only, set up on the PC).
3. **Sort panel** — date added, last modified, title, author, publisher, series; ascending or descending.
4. **Book details** — large cover, title, author, series and number, rating, tags, language, publisher, description, all custom Calibre columns, and available formats. Buttons: Read, Add to shelf, Favourite, Mark as finished, Remove download. View only: no editing of book details.
5. **Reader** — EPUB and PDF; font size and typeface; white, light cappuccino or dark background; Play Books-style controls (contents, a slider through the book, page numbers, "pages left in chapter" footer); table of contents. Search inside the book and a page-curl option come in Stage 5. Remembers the place in every book and syncs it between devices. `.doc` and other formats open in another app.
6. **Opening books from the device** — EasyReader appears in Android's "Open with" list for EPUB and PDF files and can be set as the default app for them. A book opened this way is read straight away; its place is remembered on that device only. An **Add to Calibre** button uploads it to the library, after which it syncs like any other book.
7. **Upload** — pick one or more files on the device; each is sent straight to Calibre with a progress bar, and Calibre reads the title and author from the file.
8. **Settings**
    - Server address, username and password; switch between Calibre libraries.
    - Reader background (white / light cappuccino / dark).
    - Mark as finished at (default 95%).
    - Progress bar on covers (on/off); resume last book on launch (on/off).
    - Library sync every 10 minutes, plus a Sync Now button with a "Last synced" time underneath.
    - Remove finished downloads after 7 days.

**Offline:** opening a book downloads it automatically. Downloaded books that are finished are removed from the device after 7 days; unfinished ones stay until removed by hand.

## Sync and data stored in Calibre

Everything shared between devices is saved in Calibre itself, in four custom columns, so the phone, the tablet and Calibre on the PC always agree.

| Column (lookup name) | Calibre column type | Holds |
| --- | --- | --- |
| `#er_shelves` | Comma separated text, like tags | The shelves a book is on |
| `#er_favourite` | Yes/No | Favourite or not |
| `#er_finished` | Yes/No | Finished or not |
| `#er_position` | Long text, like comments | Place in the book, % progress, and when and on which device it was saved (JSON) |

- **Reading activity (instant):** position, shelves, favourites and finished status are sent to the server as they happen. Position is saved every few pages and when leaving the book.
- **Opening a book:** the app fetches the latest position from Calibre first.
- **Offline:** changes are queued on the device and sent the next time it connects.
- **Conflicts:** if two devices disagree, the most recently saved position wins.
- **Library details:** copied to the device (not the book files), so browsing and search work instantly and offline. Refreshed every 10 minutes, or straight away with Sync Now (Settings, home menu, or pull down on the home screen).

## Server setup

See [SERVER-SETUP.md](SERVER-SETUP.md).

## Technical approach and build

- **Connection to Calibre:** the content server's data endpoints — the same ones Calibre's own web interface uses — to read the full library (all fields, custom columns, covers, formats, virtual libraries, saved searches), download books, upload books and save the four custom columns. These endpoints are not officially documented, so all server code lives in one self-contained module that is quick to fix if a Calibre update changes something.
- **App:** Kotlin, Jetpack Compose, Material 3; local database (Room) for library details; WorkManager for the 10-minute sync, the instant-sync queue and the 7-day clean-up.
- **Reader:** Readium Kotlin Toolkit (open source) for EPUB and PDF.
- **Android version:** minimum Android 13 (API 33).
- **Builds:** every push triggers a GitHub Actions build; finished versions are published on the repo's Releases page as a signed `.apk`. The signing key is kept as a GitHub secret, so updates install over the top without losing data.
- **Installing:** download the `.apk` from Releases on each device and allow "Install unknown apps" for the browser once.

**Build order** — each stage ends with an installable version the owner tests before the next starts:

1. Connect to the server, log in, and show the library as covers. Includes the project skeleton, GitHub Actions build and Releases.
2. Browse, search, filters, sort and book details.
3. Reader with backgrounds, offline downloads and reading position sync; opening EPUB and PDF files from the device.
4. Shelves, favourites, finished status and smart shelves.
5. Uploads (including Add to Calibre for books opened from the device), settings (including Sync Now), tablet layout, clean-up and polish; search inside a book; page-curl animation (a Slide/Curl choice in the reader's Aa panel).

## Out of scope for version 1

- Bookmarks, highlights and notes
- Dictionary lookup
- Read-aloud
- Editing book details from the app
- A comics reader (CBZ/CBR)
- Background colour choice for the rest of the app (reader only for now)
- Choosing the accent colour
- Other users or other platforms (Windows, iPhone)
