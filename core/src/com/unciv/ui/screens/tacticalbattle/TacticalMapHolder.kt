package com.unciv.ui.screens.tacticalbattle

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.UncivGame
import com.unciv.logic.battle.tactical.TacticalBattleContext
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.components.widgets.ZoomableScrollPane
import com.unciv.models.ruleset.unique.LocalUniqueCache

/**
 * Scrollable/zoomable sub-map showing only the tiles involved in the tactical battle.
 * Unit actors are placed in [unitLayer] which sits on top of the tile groups.
 */
class TacticalMapHolder(context: TacticalBattleContext) : ZoomableScrollPane(20f, 20f) {

    private val tileGroupMap: TileGroupMap<TileGroup>
    private val tileGroups: List<TileGroup>
    private val tileToGroup: Map<Tile, TileGroup>
    lateinit var tileSetStrings: TileSetStrings
        private set

    /** Add TacticalUnitActors here — they render above tiles, in TileGroupMap coordinates. */
    val unitLayer = Group()

    init {
        minZoom = 0.5f
        maxZoom = 5.0f

        tileSetStrings = TileSetStrings(
            UncivGame.Current.gameInfo!!.ruleset,
            UncivGame.Current.settings
        )

        // Create a TileGroup for each tile in the battle radius.
        // isForceVisible = true so tiles render without fog-of-war.
        tileGroups = context.tiles.map { tile ->
            TileGroup(tile, tileSetStrings).also { it.isForceVisible = true }
        }
        tileToGroup = tileGroups.associateBy { it.tile }

        tileGroupMap = TileGroupMap(this, tileGroups, false)

        // Update each tile group once so terrain/features render correctly.
        val uniqueCache = LocalUniqueCache(false)
        for (tg in tileGroups) {
            tg.update(null, uniqueCache)
            // Hide unit layers — units are represented by TacticalUnitActors instead
            tg.layerUnitArt.isVisible = false
            tg.layerUnitFlag.isVisible = false
        }

        // Unit actors layer sits above everything in the tile map.
        // Must match tileGroupMap size so it isn't culled when the viewport scrolls.
        unitLayer.setSize(tileGroupMap.width, tileGroupMap.height)
        tileGroupMap.addActor(unitLayer)

        actor = tileGroupMap
    }

    /**
     * Returns the center position of [tile] in TileGroupMap coordinates,
     * accounting for the internal offset TileGroupMap applies after layout.
     * Use this to correctly position unit actors on the map.
     */
    fun getWorldPos(tile: Tile): Vector2? {
        val tg = tileToGroup[tile] ?: return null
        return Vector2(tg.x + tg.width / 2f, tg.y + tg.height / 2f)
    }

    /**
     * Scrolls the map so [tile] is centered in the viewport.
     * Call after the stage has been laid out (e.g. from show()).
     */
    fun centerOnTile(tile: com.unciv.logic.map.tile.Tile) {
        if (width == 0f) { scrollPercentX = 0.5f; scrollPercentY = 0.5f; return }
        val pos = getWorldPos(tile) ?: run { scrollPercentX = 0.5f; scrollPercentY = 0.5f; return }
        scrollX = (pos.x - width / 2f).coerceAtLeast(0f)
        scrollY = (pos.y - height / 2f).coerceAtLeast(0f)
        updateVisualScroll()
    }
}
