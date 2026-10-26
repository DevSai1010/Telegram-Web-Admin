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

## What is in v0.1 (and what is not yet)
In: sign-in, chat list (bot / Telegram views, filters, search, unread counters), conversation view (reversed list,
day separators, M3 bubbles with the same corner rules as the web panel, reply quote → jump & flash, photo / sticker
inline, jump-to-latest button with new-message count), send text (+ reply, "as me / as bot" in business chats),
devices screen (rename · sign out · last activity), notification permission + battery-optimisation cards,
connection bar (connecting / waiting for network / updating), offline cache.
Not yet: voice / video / GIF playback, sending media, stickers panel, folders, search inside chats, jump-to-date,
a background service + FCM push (messages arrive instantly while the app is open; closed-app delivery is the next step).

## Tests
`gradle testDebugUnitTest` runs `LogicTest` (cache-merge rule, unread rule, back-off, bubble side, avatar colours).
The CI runs it before building.
