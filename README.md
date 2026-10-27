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

## What is in v0.2 (and what is not yet)
In: everything from v0.1 (sign-in, chat list with bot / Telegram views + filters + search + unread counters, conversation view with
M3 bubbles, reply-quote jump & flash, jump-to-latest button, offline cache, devices screen, connection bar) **plus**
- **Voice messages** play in the bubble (play / pause / seek); **video** and **GIF** open in a full-screen player (GIFs loop silently).
- **Sending**: photo / video from the gallery, camera photo, any file (≤ 50 MB, streamed — never held in memory), and **voice notes**
  (microphone permission asked only when you tap the mic; OGG/Opus on Android 10+, AAC before that — the server converts it).
- **Notifications** for new customer messages while the app is not on screen; tapping one opens that chat.
- **Always-on connection** (Settings, off by default): a small foreground service keeps the live connection with the app closed.
- **Language** picker: system / فارسی / English / Русский (switches text direction too).
Not yet: FCM push (needs a Firebase project — see below), opening / saving documents, the sticker panel and sending stickers, folders,
search inside chats, jump-to-date, contacts names, release-signed builds.

## FCM (push while the app is fully closed)
Needs things only you can create: a Firebase project, `google-services.json` for package `com.msma.tgpanel`, and a service-account key
stored as a Space secret. Until then "Always-on connection" is the way to receive messages with the app closed.

## Tests
`gradle testDebugUnitTest` runs `LogicTest` (cache-merge rule, unread rule, back-off, bubble side, avatar colours).
The CI runs it before building.
