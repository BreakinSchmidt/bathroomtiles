# RadialTiles: Wear OS Home Assistant Controller

A high-performance, circular-first Wear OS application built for round smartwatches (specifically optimized for the **Google Pixel Watch 4 45mm**).

## Features

- **Multiple Swipeable Home Screen Tiles**:
  - Up to 4 independent Wear OS Tiles (`Radial Tile 1`, `Radial Tile 2`, `Radial Tile 3`, `Radial Tile 4`).
  - **In-Place Control**: Tap buttons directly on the home screen tile to toggle devices in the background without launching the app!
  - Tapping the room title opens the full interactive app.
- **Dynamic Circular Layouts (Zero Center Dead-Zone)**:
  - **1 to 5 Buttons**: Large pie-chart slices meeting at the center point, maximizing the 45mm circular AMOLED screen.
  - **6 Buttons**: Automatically renders a contoured 2-column by 3-row grid fitting the circular bezel.
  - Distinct customizable colors per button with transparent borders.
  - Rotary crown support to smoothly page between rooms.
- **Multi-Sensory Feedback Engine**:
  - **Haptics**: Bespoke Pixel Watch LRA vibration waveforms (rising double pulse for Turn ON, soft downward tick for Turn OFF, rhythmic pulse sequence for Scenes, micro tick for crown scrolling).
  - **Audio**: Low-latency mechanical clicks and chimes with settings mute switch.
- **Phone Web Configurator**:
  - Embedded HTTP server on port 8080.
  - Scan the QR code on the watch to open the web portal on your phone.
  - Test connection, load HA entities automatically, customize each tile with a live circular preview, and save all tiles in one click!

## Adding Tiles to Your Watch Carousel

### On Watch:
1. Long-press any existing tile on your watch face carousel.
2. Swipe right to the end and tap **"+" (Add tile)**.
3. Select **Radial Tile 1**, **Radial Tile 2**, **Radial Tile 3**, or **Radial Tile 4**.

### In Pixel Watch Phone App:
1. Open the **Pixel Watch** app on your phone.
2. Tap **Tiles** -> **Add tile**.
3. Select any of the **Radial Tiles** and position them in your carousel.
