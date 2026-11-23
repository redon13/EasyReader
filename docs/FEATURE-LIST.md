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

## Done

### Version 1.2.0

- **Read aloud** – EPUBs can be read aloud with a natural Google voice (Chirp 3 HD, Iapetus by default; any Chirp 3 HD voice can be chosen in Settings after listening to a sample). When this month's free Chirp allowance is nearly used, it moves on to Google WaveNet, then to the phone's own voice, which is also used offline or without a key. Keeps reading with the screen off, with controls on the lock screen; highlights the sentence being read and turns pages by itself; speed 0.75×–2×. Setup: [READ-ALOUD-SETUP.md](READ-ALOUD-SETUP.md).

### Version 1.1.1

- **Page curl rebuilt** – during a turn the app draws both pages itself from pictures, so the real page can't flash through; recent pages are remembered, so turning straight back is instant; sideways swipes go to the curl from the first touch; fixed the old page flashing at the end of a turn back; lighter back of the page in dark mode and a crisper finish.

### Version 1.1.0

- **Keep the screen on while reading** – the screen stays on while the reader is open; the library and other screens use the phone's normal timeout.
- **Screen brightness in the reader's Aa panel** – a brightness slider with a "Use phone brightness" switch (on by default). It applies only while reading, and one brightness is used for all backgrounds.
- **Smoother page curl** – the picture of the page is taken in advance so a turn starts instantly; drawing reuses its working memory to avoid stutters; shading across the fold makes the page look gently rolled; and the turn finishes at about the speed of the flick, easing out gently.

For everything in 1.0, see the [spec](SPEC.md).
