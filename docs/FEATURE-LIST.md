# EasyReader – feature list

Ideas and requests for after version 1.0, in no fixed order. Each one is planned with the owner and approved before any work starts. When an item is built, it moves to **Done** with the version it arrived in.

## Planned

### 1. Warn when my reading place hasn't reached the server

When a device is offline, the reading place is saved on the device and sent to Calibre later. Until then, the other device doesn't know where I got to. This makes that visible and gets the place sent sooner.

- **Closing a book** (back arrow / Back button): first try to send the place, showing "Saving your place…" for up to about 5 seconds. If the server can't be reached, ask **Try again** / **Close anyway**, explaining that the other device won't know where I am until it's sent.
- **Leaving any other way** (Home button, switching apps, Android closing the app) while the place isn't sent: show a notification, "Your place in [book] isn't synced yet". It disappears by itself once sent. Needs notification permission (Android asks once).
- **Once back online:** try sending about every minute (not longer and longer), and send waiting places on every library sync.
- A small **"Offline"** label in the reader's footer while the latest place hasn't been sent.
- *To decide:* also warn when **opening** a book while offline ("may not be your latest place")? Suggested: yes.

### 2. Keep the screen on while reading

- The screen doesn't dim or turn off while EasyReader is on screen, so reading isn't interrupted. Normal screen timeout applies after leaving the app.
- *To decide:* only in the **reader**, or anywhere in the app? Suggested: only in the reader.

### 3. Screen brightness in the reader's Aa panel

- A **brightness slider** in Aa, alongside text size, typeface, page turn and background.
- Changes the brightness **only while reading**. Leaving the reader returns to the phone's normal brightness.
- A **"Use phone brightness"** switch (on by default) follows the phone's own setting, including auto-brightness.
- The chosen brightness is remembered.
- *To decide:* one brightness for all backgrounds, or one per background (dark is often read dimmer)? Suggested: one.

### 4. Smoother page curl

- Make the page-curl animation feel smoother and more natural.
- *To decide:* what feels rough at the moment? For example a pause at the start of a turn, jerky movement while dragging, the speed of the finish, or the fold looking flat rather than gently rounded. A short description or screen recording will help.

## Done

Nothing yet since version 1.0. For everything in 1.0, see the [spec](SPEC.md).
