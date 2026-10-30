# Telegram Panel — Android app

Native Android client (Kotlin · Jetpack Compose · Material 3 · Room) for the panel in the Space
`https://msma-telegram-ai-bot.hf.space`. It talks only to the panel's API (see `docs/API.md` in the Space repo):
sign-in with a device label, chat list + history cached in Room, live updates over the WebSocket (`/ws`) with
`/api/sync` catch-up, sending, signed-in-devices management, notification / battery-optimisation prompts.
Languages: فارسی (default) · English · Русский (follows the system language).

## Get the APK (no Android Studio needed)
1. Create an empty repository on GitHub and push **the contents of this folder** to it (this folder must be the repo root,
   so `.github/workflows/build.yml` sits at `/.github/workflows/build.yml`).
2. Open the repo → **Actions → Build APK** (it also runs by itself on every push).
3. When the run is green, open it and download **TelegramPanel-debug-apk** from *Artifacts* → unzip → install the `.apk`
   (allow "install unknown apps" for your file manager / browser once).
4. If the run is red, open the failed step and copy the error text — that is exactly what is needed to fix it.

The debug APK is signed with Android's debug key: fine for your own devices. A release-signed build comes later.

## Run on a computer instead
Android Studio (Ladybug or newer) → *Open* this folder → *Run*. JDK 17.

## What is in v0.3 — the screens now follow the web panel
The sign-in card, chat list, chat screen, composer, message menu and profile sheet were rebuilt from the web panel's own markup / CSS
(sizes, radii, colours, wording, order of controls), with the same Persian texts.

- **Sign-in** — one card: brand mark, title, hint line, centred fields, button, *Forgot password* (temporary password sent to Telegram) and the
  *choose a new password* step after a temporary-password sign-in; suspended-account banner. The server address is behind a small link.
- **Chat list** — pill search bar with the menu button, connection pill, two-segment bot / Telegram switch with unread badges, filter chips with
  counts, rows (52 dp avatar, pin / mute / business / AI-pending marks, unread badge), pencil FAB + *New chat* dialog (id or @username + first message).
- **Chat** — 64 dp top bar (back · avatar · title · id; tap = profile), bubbles with the web's corner rules, swipe-to-reply, long-press menu
  (six most-used reactions + expand, reply, edit, copy, add to panel, forward, pin in groups, delete), reaction chips under the bubble,
  jump-to-latest button with count.
- **Composer** — attach · emoji · sticker · text · mic↔send in one 28 dp container; attach menu (photo, video, GIF, file, + camera); emoji panel with the
  web's own 8 categories; sticker panel (recent + the packs from settings) and GIF tab; reply strip; voice recording bar.
- **Profile sheet** — photo, id / name / username / bio, shared-media tabs (photos, videos, voice, files), pin · mute · AI panel · delete, per-chat AI settings.

Also: voice / video / GIF playback, media sending, notifications, always-on connection, language picker, devices screen.
Not yet matched to the web: the AI / panel / bot-profile / users settings pages, group info (members, moderation), GIF previews in the panel
(tiles show a play icon), animated (.tgs / .webm) stickers (the emoji is shown instead), opening / saving documents, FCM.

## FCM (push while the app is fully closed)
Needs things only you can create: a Firebase project, `google-services.json` for package `com.msma.tgpanel`, and a service-account key
stored as a Space secret. Until then "Always-on connection" is the way to receive messages with the app closed.

## Tests
`gradle testDebugUnitTest` runs `LogicTest` (cache-merge rule, unread rule, back-off, bubble side, avatar colours).
The CI runs it before building.
