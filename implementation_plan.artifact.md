# Implementation Plan - Advanced Features & TV Support

This plan outlines the implementation of P2P (Torrent) streaming, Advanced Player Settings, Deep Linking, and initial Android TV (Leanback) support.

## Proposed Changes

### 1. Build Configuration
#### [MODIFY] [app/build.gradle.kts](file:///B:/ultraplay/ultraplay/app/build.gradle.kts)
- Add `libtorrent4j` for P2P support.
- Add `NanoHTTPD` or `Ktor` for local streaming server.
- Add `androidx.leanback:leanback` (optional, for some TV utils).

### 2. Deep Linking
#### [MODIFY] [AndroidManifest.xml](file:///B:/ultraplay/ultraplay/app/src/main/AndroidManifest.xml)
- Add `<intent-filter>` for `stremio://` schema to `MainActivity`.

#### [MODIFY] [MainActivity.kt](file:///B:/ultraplay/ultraplay/app/src/main/java/com/ultrastream/app/MainActivity.kt)
- Handle incoming intents in `onCreate` and `onNewIntent`.
- Parse `stremio://` URLs and navigate to appropriate screens (Details or Addons).

### 3. P2P Streaming Engine
#### [NEW] [TorrentEngine.kt](file:///B:/ultraplay/ultraplay/app/src/main/java/com/ultrastream/app/player/TorrentEngine.kt)
- Initialize `libtorrent` session.
- Manage torrent downloads and local HTTP streaming.
- Provide a `streamUrl` (localhost) to ExoPlayer.

### 4. Advanced Player Settings
#### [MODIFY] [PlayerViewModel.kt](file:///B:/ultraplay/ultraplay/app/src/main/java/com/ultrastream/app/ui/screens/player/PlayerViewModel.kt)
- Implement `setSubtitleStyle(color, size, background)`.
- Implement `setAudioDelay(ms)` and `setSubtitleDelay(ms)`.
- Expose `audioTracks` to UI.

#### [MODIFY] [PlayerScreen.kt](file:///B:/ultraplay/ultraplay/app/src/main/java/com/ultrastream/app/ui/screens/player/PlayerScreen.kt)
- Add "Audio Selection" bottom sheet.
- Add "Sync & Styling" bottom sheet for delay and subtitle look.

### 5. Android TV Support
#### [MODIFY] UI Components
- Ensure `PosterCard`, `EpisodeCard`, and `StreamCard` use `Modifier.focusable()`.
- Add visual focus indicators (scale up or border) when focused.
- Ensure Bottom Navigation works with D-pad.

## Verification Plan

### Automated Tests
- Unit tests for Deep Link parsing logic.
- TorrentEngine initialization tests.

### Manual Verification
- Test `stremio://` links from a browser.
- Test playing a magnet link directly (if supported) or a torrent-based stream from an addon.
- Verify D-pad navigation on an Android TV emulator.
- Test subtitle styling and audio sync adjustments during playback.

## User Review Required
> [!IMPORTANT]
> P2P streaming requires significant background resources. We will implement it as a Foreground Service to prevent the system from killing the download during playback.
