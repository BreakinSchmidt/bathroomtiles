# RadialTiles (bathroomtiles) - Antigravity Agent Guidelines

## Project Overview
**RadialTiles** is a Wear OS application (package `com.radialtiles`) optimized for circular smartwatches (e.g., Pixel Watch 45mm circular AMOLED) to provide fast, tactile, glanceable control of **Home Assistant** entities.

---

## Build, Test & Deployment Commands

Always use Windows-native PowerShell commands:

- **Compile debug APK**: `.\gradlew.bat assembleDebug`
- **Run unit tests**: `.\gradlew.bat test`
- **Build & run tests**: `.\gradlew.bat assembleDebug test`
- **Deploy to watch via ADB**:
  ```powershell
  .\deploy.ps1 -Device "192.168.11.123:39863"
  ```
  - APK output: `app\build\outputs\apk\debug\app-debug.apk`
  - ADB binary: `C:\Users\mschm\AppData\Local\Android\Sdk\platform-tools\adb.exe`
  - Default watch endpoint: `192.168.11.123:39863`

---

## UI & Layout Rules

1. **Carousel Tile-Only Dial Architecture**:
   - **There is NO in-app dial**. All dial button controls exist exclusively as **Wear OS Carousel Tiles / Widgets** (`com.radialtiles.tile`).
   - The in-app activity (`MainActivity`) is used **strictly for Settings and Phone Setup** (`SettingsScreen` and `ConfigServerScreen`).
   - **No Tile Header Titles**: Do not display a room/tile title header at the top of carousel tiles to preserve maximum space on circular AMOLED screens.

2. **Dial Geometry & Layout Modes (Bitmap Rendering in ProtoLayout)**:
   - **1 to 6 Buttons**: Rendered as a radial pie dial with sector slices (`renderPieBitmap` in `RadialTileBitmapRenderer`).
   - **> 6 Buttons**: Rendered as a 2-column by 3-row contoured rounded-cell grid (`renderGridBitmap` in `RadialTileBitmapRenderer`).
   - Backgrounds must remain pure AMOLED black (`#000000`) for battery efficiency on OLED displays.

---

## Architecture & System Patterns

### 1. Wear OS Carousel Tiles (`com.radialtiles.tile`)
- `RadialTile1Service` through `RadialTile4Service` extend `BaseRadialTileService`.
- Maps to pages configured in `AppConfiguration.pages` (up to 4 carousel tiles).
- **Bitmap Rendering**: Since ProtoLayout lacks freeform canvas path drawing, `RadialTileBitmapRenderer` creates a 454x454 PNG bitmap of the dial.
- **Touch Sectors**: Transparent ProtoLayout `Box` elements with `LoadAction` IDs (`toggle:entityId:domain`) overlay the rendered bitmap to detect clicks without vector UI constraints.
- **In-Place Toggle**: `BaseRadialTileService.onTileRequest` checks `lastClickableId` to toggle Home Assistant entities and trigger haptics instantly without launching the main activity.

### 2. Tap Feedback & Tile Interaction Architecture
- **No Stuck Highlights**: Sub-second bitmap animation decay hacks (`TimeInterval`, coroutine `delay`, and `TileService.getUpdater().requestUpdate(...)`) are fundamentally unsupported on Wear OS carousel tiles because `requestUpdate` is aggressively rate-limited and dropped by Wear OS System UI (`PTUpdateScheduler`), causing buttons to get permanently stuck on.
- **Native Visual Feedback**: Touch sectors use `ModifiersBuilders.Clickable.Builder().setVisualFeedbackEnabled(true)` so the Wear OS System UI renderer natively draws client-side tap highlight/ripple directly over the touched sector with zero IPC latency and automatic decay.
- **Instant Sensory Response**: Button clicks immediately trigger Pixel Watch LRA haptic feedback (`PRIMITIVE_CLICK`, `PRIMITIVE_THUD`), giving a crisp, physical tactile confirmation.
- **Atomic Token Deduplication**: Click IDs embed dynamic tokens (`toggle:entityId:domain:tok_page_count`). Handled tokens are tracked in an LRU set in `BaseRadialTileService` to prevent duplicate / ambient re-triggers.
- **RAM Image Cache**: `RadialTileBitmapRenderer.getNormalDialBytes` caches the 454x454 PNG bitmap in memory, ensuring `onTileResourcesRequest` completes in 0ms without disk or canvas overhead.

### 3. In-App Setup & Settings (`com.radialtiles.presentation.screens`)
- `MainActivity`: Kept awake with `FLAG_KEEP_SCREEN_ON` during configuration. Navigates between `SETTINGS` and `CONFIG_SERVER`.
- `SettingsScreen`: Haptics toggle, audio toggle, and button to launch phone setup portal.
- `ConfigServerScreen`: Shows local IP and port when phone configurator is active.

### 4. Embedded Phone Setup Portal (`com.radialtiles.data.server`)
- `WebConfigServer`: NanoHTTPD embedded HTTP server listening on port 8080.
- `portal.html`: Full mobile-friendly web UI served from assets allowing users to configure HA URL, token, entities, MDI icons, colors, and layout from their phone.

### 5. Sensory Feedback (`com.radialtiles.feedback`)
- `HapticFeedbackManager`: Uses Pixel Watch LRA haptic primitives (`PRIMITIVE_CLICK`, `PRIMITIVE_THUD`, `PRIMITIVE_TICK`).
- `AudioFeedbackManager`: Soundpool/audio click chimes for confirmations.

### 6. Icon Mapping (`com.radialtiles.util`)
- `IconMapper`: Maps Home Assistant / Material Design Icon names (e.g. `mdi:fan`, `mdi:shower`, `mdi:lightbulb`) to Unicode glyphs rendered onto dial canvases.
