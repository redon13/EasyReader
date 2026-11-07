# Server setup

Three one-off jobs on the Windows 11 PC: set up Calibre's content server, add four custom columns, and install Tailscale. No router changes or open ports are needed.

## 1. Calibre content server

- [ ] Install Calibre and add your books.
- [ ] **Preferences → Sharing over the net:** tick "Require username and password to access the Content server" and "Run server automatically when calibre starts".
- [ ] **User accounts** tab (same screen): add your user and allow it to make changes. EasyReader needs this for uploads, shelves, favourites, finished status and reading position.
- [ ] **Connect/share → Start Content server.** Allow it through Windows Firewall when asked. Note the port shown (usually 8080).
- [ ] Put a Calibre shortcut in the Windows Startup folder (press Win+R, type `shell:startup`, paste the shortcut there) so Calibre starts with the PC.
- [ ] **Windows Settings → System → Power:** set sleep to Never when plugged in.

## 2. Custom columns

**Preferences → Add your own columns → Add custom column**, once for each row below. Use exactly these lookup names and types, then restart Calibre.

| Lookup name | Column heading | Column type |
| --- | --- | --- |
| `er_shelves` | Shelves | Comma separated text, like tags, shown in the Tag browser |
| `er_favourite` | Favourite | Yes/No |
| `er_finished` | Finished | Yes/No |
| `er_position` | Reading position | Long text, like comments, not shown in the Tag browser |

(Calibre adds the `#` in front of the lookup name itself.)

## 3. Tailscale (remote access)

- [ ] Create a free Tailscale account at tailscale.com and install Tailscale on the PC.
- [ ] Install the Tailscale app on the tablet and the phone, signed in to the same account.
- [ ] In the Tailscale app, find the PC's name (for example `my-pc`).
- [ ] EasyReader's server address is then `http://<pc-name>:8080` (use the port from step 1).

## Check it works

On the tablet, with Tailscale switched on, open `http://<pc-name>:8080` in the browser. You should see Calibre's login prompt, then your library.
