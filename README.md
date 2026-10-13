# Sonora — Your Music. Your Space.

> **Premium Native Android Offline Music Player**  
> Built with modern Kotlin, Jetpack Compose, AndroidX Media3 ExoPlayer, MediaSessionService, Room, and DataStore.

---

## 1. Product Overview

**Sonora** is a refined, native Android offline music player crafted around the philosophy that **your music belongs to you**. Designed as an immersive personal listening room and archive, Sonora discovers, indexes, and plays audio files stored locally on the device—with **zero internet connection, no remote tracking, and no accounts required**.

### Key Highlights
- **100% Offline & Private:** Operates strictly on local content URIs via Android's `MediaStore`. No remote server or online streaming dependency.
- **Background Playback Engine:** Powered by AndroidX **Media3 ExoPlayer** coupled with a dedicated `MediaSessionService`. Audio keeps playing when the app is minimized or the screen is locked.
- **Audio Focus & Headset Awareness:** Integrates system audio focus handling (ducking/pausing during calls or alarms) and responds to `ACTION_AUDIO_BECOMING_NOISY` to pause playback when headphones or Bluetooth devices disconnect.
- **System Notification & Lock Screen Controls:** Full MediaStyle notification with playback scrub, skip, pause/play, and track metadata synchronization.
- **Safe Persistence Model:** Built with **Room Database**. User-created playlists, favorites, and listening history are strictly isolated from file scanning. A library rescan never deletes your playlists.
- **Original Sonora Design System:** Distinctive visual identity built with Electric Orange (`#FF6500`), Deep Blue (`#1E3E62`), Midnight Navy (`#0B192C`), and Pure Black (`#000000`). Supports immediate switching between **Light**, **Dark**, and **Follow System** themes.

---

## 2. Brand Identity & Visual Design

Sonora features a bespoke four-color brand palette:

| Token | Hex Value | Role |
| :--- | :--- | :--- |
| **Electric Orange** | `#FF6500` | Primary interactive accent, play controls, scrub indicators, active state |
| **Deep Blue** | `#1E3E62` | Secondary brand surface, containers, accents |
| **Midnight Navy** | `#0B192C` | Dark theme main background canvas, depth elements |
| **Pure Black** | `#000000` | Contrast foundation & AMOLED playback surfaces |

### Themes
1. **Dark Theme:** `#0B192C` background, `#13243A` elevated surfaces, `#1E3E62` primary surfaces, crisp `#F5F7FB` primary typography.
2. **Light Theme:** `#F4F6FA` daylight background, `#FFFFFF` elevated cards, `#0B192C` deep text, `#FF6500` accents.
3. **Follow System:** Dynamically reflects the Android operating system theme and updates window status/navigation bar colors seamlessly.

---

## 3. Architecture & Code Structure

Sonora follows Clean Architecture and single-Activity architecture with unidirectional data flow (UDF):

```
app/src/main/java/com/sonora/music/
├── SonoraApp.kt                     # Application class & dependency initialization
├── MainActivity.kt                  # Single Activity, edge-to-edge, runtime permissions
├── audio/
│   ├── SonoraPlaybackService.kt     # Media3 MediaSessionService for background playback
│   └── SonoraAudioController.kt     # MediaController UI bridge with reactive StateFlow
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── SonoraDatabase.kt    # Room database setup
│   │   │   ├── dao/                 # SongDao, PlaylistDao, HistoryDao
│   │   │   └── entity/              # SongEntity, PlaylistEntity, CrossRef, HistoryEntity
│   │   ├── preferences/
│   │   │   └── SonoraPreferences.kt # DataStore for theme, sorting, and history settings
│   │   └── scanner/
│   │       └── MediaScanner.kt      # Scoped MediaStore discovery & metadata extraction
│   ├── model/                       # Domain models: Song, Album, Artist, Playlist, Folder, etc.
│   └── repository/
│       └── MusicRepository.kt       # Repository orchestrating persistence, scanner, and preferences
├── ui/
│   ├── theme/                       # Color tokens, Typography, Shapes, SonoraTheme
│   ├── components/                  # SongItemRow, MiniPlayer, AlbumArtImage, Dialogs, EmptyState
│   ├── navigation/                  # SonoraNavHost, routes, and smooth page transitions
│   └── screens/
│       ├── main/MainScreen.kt       # Editorial hub with Songs, Albums, Artists, Playlists, Folders
│       ├── player/NowPlayingScreen.kt # "The Listening Room" full-screen playback experience
│       ├── queue/QueueScreen.kt     # Playback queue manager (reorder, remove, clear)
│       ├── search/SearchScreen.kt   # Instant offline search with live filtering
│       ├── settings/SettingsScreen.kt # Theme mode, sorting defaults, rescan, history controls
│       ├── permissions/PermissionScreen.kt # First-run onboarding & permission rationale
│       └── details/                 # AlbumDetail, ArtistDetail, PlaylistDetail, FolderDetail
└── viewmodel/
    └── MainViewModel.kt             # UI state holder & event coordinator
```

---

## 4. Audio Engine & Background Playback

The audio playback subsystem is built on **AndroidX Media3 (1.5.0)**:
- **ExoPlayer:** Handles decoding, buffer management, and stream playback from local content URIs (`content://media/external/audio/media/...`).
- **MediaSessionService:** Runs as a foreground service with `foregroundServiceType="mediaPlayback"`.
- **MediaController:** Asynchronously connects the Compose UI to the service. All UI actions (`playSong`, `togglePlayPause`, `seekTo`, `skipNext`, `skipPrevious`, `toggleShuffle`, `cycleRepeatMode`) pass through the `MediaController`.
- **System Synchronization:** Headset disconnections trigger `AudioManager.ACTION_AUDIO_BECOMING_NOISY`, immediately pausing the player to prevent unwanted loudspeaker playback.

---

## 5. Local Discovery & MediaStore Integration

- Sonora queries `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` with Scoped Storage compatibility.
- Extracts track titles, artists, albums, album IDs, durations, track numbers, release years, date added, MIME types, and folder paths.
- Automatically discards sound files under 5 seconds (ringtones, notifications).
- Embedded album art is accessed via content URIs (`content://media/external/audio/albumart/{albumId}`) and loaded asynchronously with **Coil**, accompanied by a Sonora geometric vinyl placeholder.
- **Reconciliation:** Scans update metadata while preserving user favorites, play counts, and timestamps. Playlists are preserved permanently.

---

## 6. Permissions & Android Versions

| Android Version | Permissions Required |
| :--- | :--- |
| **Android 13+ (API 33+)** | `READ_MEDIA_AUDIO`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` |
| **Android 8.0 - 12 (API 26-32)** | `READ_EXTERNAL_STORAGE`, `FOREGROUND_SERVICE` |

Sonora includes an onboarding permission screen that clearly explains why media storage access is needed before requesting permissions at runtime.

---

## 7. Build and Run Instructions

### Prerequisites
- JDK 17 or JDK 21 installed.
- Android SDK with Platforms 34/35 installed (`sdk.dir` configured in `local.properties`).

### Building from Command Line
```powershell
# Clone the repository
git clone https://github.com/KalpitM2527/Sonora.git
cd Sonora

# Build debug APK
.\gradlew.bat assembleDebug

# Run unit tests
.\gradlew.bat test
```

### Generated APK Locations
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

### Generating a Signed Release APK
```powershell
.\gradlew.bat assembleRelease
```
*Note: Ensure your release signing configuration is defined in your local environment or CI/CD secrets.*

---

## 8. Testing

Sonora includes unit tests covering:
- Track duration formatting (`formattedDuration`)
- Repeat mode state transitions (`OFF` -> `ALL` -> `ONE` -> `OFF`)
- Scrub progress calculations (`PlaybackUiState.progress`)
- Song sorting algorithms across all dimensions (Title, Duration, Date Added, etc.)
- Playback queue item reordering and index management
- Favorite state toggling logic

To run all unit tests:
```powershell
.\gradlew.bat test
```

---

## 9. License

Sonora is open source software released under the [Apache 2.0 License](LICENSE).
