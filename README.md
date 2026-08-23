# Offline Music

A small Android music player written in Kotlin and Jetpack Compose. It has no accounts, cloud database, cloud sync, or public-download dependency: selected audio files are copied into the app's private storage and remain playable offline.

## Features

- Import audio from Android's system file picker or download a direct audio URL
- Private offline music library, search, embedded artwork, and metadata
- Play, pause, seek, previous, and next controls using Media3 ExoPlayer
- Persistent song catalog using Room / SQLite
- Delete removes both the Room row and its private audio/artwork files

## Architecture

The project uses a deliberately small, extendable split:

- `data`: Room entity/DAO/database plus `SongRepository`. The repository owns import, metadata extraction, private-file cleanup, and database updates.
- `player`: `PlayerViewModel` owns one ExoPlayer and exposes playback state and the Room song flow to the UI.
- `MainActivity`: Compose UI and Android file-picker integration. It contains the library/search screen and mini-player controls.

This keeps Android storage and database details out of the UI, making later additions such as playlists, downloads, or a background playback service straightforward.

## Setup

1. Open the repository in Android Studio (recent stable release with JDK 17).
2. Let Gradle sync and install the Android SDK for API 35 when prompted.
3. Run on an Android 8.0+ device or emulator.
4. Tap **+** and choose an audio file, or tap the download icon to retrieve a direct audio URL. The app copies it before playback, so the original can later be removed without affecting the private library.

## Local storage and Room

Imported source URIs are read once through the Storage Access Framework and copied to `filesDir/audio/<uuid>.audio`. Embedded cover art, if present, is saved in `filesDir/artwork/<uuid>.jpg`. Both directories are internal app storage: other apps and normal phone `Downloads` cannot browse them, and Android deletes them if this app is uninstalled.

Room stores the `songs` table in the app's private SQLite database (`music.db`). Each row holds the generated ID, title, artist, album, duration, internal audio path, optional artwork path, and import timestamp. Audio binaries are not stored in SQLite, keeping queries fast and the database compact.

## Command-line builds

This repository includes the Gradle Wrapper, so Android Studio is not required. Install a JDK 17+ and the Android SDK Platform 35 / Build Tools using the Android command-line tools, then set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) and `JAVA_HOME`. From the project root run:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux, first make the launcher executable (`chmod +x gradlew`) and run `./gradlew assembleDebug`. The first run downloads Gradle 8.10.2 and project dependencies into the current user's Gradle cache; the wrapper verifies the Gradle distribution with its SHA-256 checksum.
