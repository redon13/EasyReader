# Read aloud – setting up the Google voice

EasyReader can read EPUB books aloud. Without any setup it uses the phone's own voice. With a Google Cloud key it uses Google's much more natural voices:

1. **Chirp 3 HD** (your chosen voice, Iapetus by default) – free for the first 1 million characters each month. EasyReader stops at 900,000 to stay safely inside the free allowance.
2. **WaveNet** – free for the first 4 million characters each month. EasyReader stops at 3.6 million.
3. **The phone's own voice** – once both are used up, when offline, or without a key.

An average novel is roughly 500,000 characters, so Chirp 3 HD alone covers about two books a month. The limits can be changed in **Settings → Read aloud → Limits**.

The key is stored encrypted on the device and never goes in this repository.

## Creating the key (once)

You already have the **EasyReader** project in Google Cloud with Text-to-Speech switched on and a budget alert. What's left:

1. Open EasyReader → **Settings → Read aloud**. Under **Details for locking the key** you'll see the package name and a SHA-1 fingerprint. Tap **Copy** (you can paste these into a note to have them handy).
2. On a computer, go to [console.cloud.google.com](https://console.cloud.google.com), make sure the **EasyReader** project is selected at the top.
3. Menu (☰) → **APIs & Services → Credentials**.
4. **+ Create credentials → API key**. A key is created; click **Edit API key** (or the key's name).
5. Name it `EasyReader`.
6. **Application restrictions:** choose **Android apps** → **Add** → paste the **package name** and the **SHA-1 fingerprint** from step 1 → **Done**.
7. **API restrictions:** choose **Restrict key** → tick **Cloud Text-to-Speech API** only.
8. **Save**. Then click **Show key** and copy it.
9. Get the key to the phone (for example, email it to yourself), then in EasyReader: **Settings → Read aloud → Google voice key → Add**, paste, **Save**. Delete the email afterwards.

With those two restrictions, the key only works from EasyReader on your devices, and only for reading aloud – useless to anyone else.

## Choosing the voice

**Settings → Read aloud → Voice → Choose.** Switch between **English (UK)** and **Magyar** to hear samples, press ▶ next to a voice, and tap a name to choose it. The same voice is used for every language. Each sample uses about 60 characters of the monthly allowance.

## Using it

- In a book, tap the middle of the page to show the bars, then the **headphones** button at the top. Reading starts from the top of the page showing.
- The player bar has previous paragraph, play/pause, next paragraph, speed (0.75× to 2×), the voice in use and ✕ to stop.
- Reading carries on with the screen off; controls appear in the notification and on the lock screen. Unplugging headphones pauses it.
- The sentence being read is highlighted and pages turn by themselves.
- Read aloud is for EPUBs only.

## If something goes wrong

- **"Google didn't accept the key"** – check the key was pasted in full, and that the package name and SHA-1 in the key's settings match the ones shown in EasyReader.
- **"Offline – using the phone's voice"** – EasyReader tries Google again after a minute.
- **The phone's voice sounds wrong or doesn't speak Hungarian** – on the phone: Settings → General management → Text-to-speech, and install the Hungarian voice data for the speech engine.
