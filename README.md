# OpenCode Pocket (Android)

Kotlin + Jetpack Compose mobile client for an OpenCode Server running on the same Android device (Termux).

## Features
- Dark violet mobile UI
- Health check (`GET /api/info`)
- Create session (`POST /api/session`)
- Send prompt (`POST /api/session/{id}/prompt`) and wait for the agent reply
- Read session messages (`GET /api/session/{id}/message`)
- Configure local server URL and optional server password

## Termux setup (same phone)
Run OpenCode server from the project directory:
```sh
cd ~/projects/my-app
opencode serve --hostname 127.0.0.1 --port 4096
```
Set the app server address to `http://127.0.0.1:4096`.
The server prints a generated password at startup; enter it in the app settings.
Keep the server bound to localhost; do not expose it publicly.

## Build APK
Open in Android Studio and Build > Build APK(s), or run:
```sh
gradle assembleDebug
```
The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
