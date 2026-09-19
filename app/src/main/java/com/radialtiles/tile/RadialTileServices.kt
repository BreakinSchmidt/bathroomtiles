package com.radialtiles.tile

/**
 * Tile 1: Controls first dashboard page (e.g. Bathroom).
 */
class RadialTile1Service : BaseRadialTileService(0)

/**
 * Tile 2: Controls second dashboard page (e.g. Living Room).
 */
class RadialTile2Service : BaseRadialTileService(1)

/**
 * Tile 3: Controls third dashboard page (e.g. Kitchen).
 */
class RadialTile3Service : BaseRadialTileService(2)

/**
 * Tile 4: Controls fourth dashboard page (e.g. Scenes).
 */
class RadialTile4Service : BaseRadialTileService(3)

object RadialTileUpdater {
    fun requestAllTilesUpdate(context: android.content.Context) {
        val updater = androidx.wear.tiles.TileService.getUpdater(context)
        updater.requestUpdate(RadialTile1Service::class.java)
        updater.requestUpdate(RadialTile2Service::class.java)
        updater.requestUpdate(RadialTile3Service::class.java)
        updater.requestUpdate(RadialTile4Service::class.java)
    }
}
