# Creating the signing key (one time only)

Android only installs an update over the top of an app if both are signed with the same key. You make this key once, on the Windows PC, and give it to GitHub as a secret so it can sign every release. The key never goes into the repo.

It takes about 15 minutes.

## 1. Install Java (free)

1. Go to **adoptium.net** and download **Temurin 17 (LTS)** for Windows (the `.msi` file).
2. Run it. On the "Custom Setup" screen, make sure **Add to PATH** is turned on. Click through the rest.

## 2. Make the key

1. Press **Win**, type `PowerShell`, and open it.
2. Go to your Documents folder:

   ```
   cd $HOME\Documents
   ```

3. Make the key (copy and paste it as one line):

   ```
   keytool -genkeypair -v -keystore easyreader-release.jks -alias easyreader -keyalg RSA -keysize 4096 -validity 36500
   ```

4. It asks for a **keystore password**. Make up a strong one and write it down. You won't see anything as you type; that's normal. Type it again when asked.
5. It asks for your name, organisation and so on. You can fill these in or press Enter to skip each one. Type `yes` when it asks if it's correct.

You now have a file called `easyreader-release.jks` in Documents.

## 3. Keep a backup

**Copy `easyreader-release.jks` and its password somewhere safe**, such as a password manager or a USB stick. If the key is lost, future updates won't install over the top: you'd have to uninstall EasyReader (losing its downloads) and start again with a new key.

## 4. Copy the key as text

Still in PowerShell, run:

```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\Documents\easyreader-release.jks")) | Set-Clipboard
```

The key is now on the clipboard as a long line of text.

## 5. Add the secrets to GitHub

1. Open the EasyReader repo on github.com.
2. Go to **Settings → Secrets and variables → Actions**.
3. Click **New repository secret** and add these four, one at a time:

| Name | Secret |
| --- | --- |
| `EASYREADER_KEYSTORE_BASE64` | Paste (Ctrl+V) the long line from step 4 |
| `EASYREADER_KEYSTORE_PASSWORD` | The password from step 2 |
| `EASYREADER_KEY_ALIAS` | `easyreader` |
| `EASYREADER_KEY_PASSWORD` | The same password again |

That's it. Tell Claude when it's done, and it will publish the release.
