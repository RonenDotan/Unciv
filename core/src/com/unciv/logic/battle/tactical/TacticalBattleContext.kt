package com.unciv.logic.battle.tactical

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.HexMath

/**
 * Describes the set-up for a tactical battle: which tiles are in scope,
 * which units participate, and who is on which side.
 *
 * Build via [TacticalBattleContext.buildFrom].
 */
class TacticalBattleContext(
    val centerTile: Tile,
    val radius: Int,
    val tiles: List<Tile>,
    val playerUnits: List<TacticalUnit>,
    val enemyUnits: List<TacticalUnit>
) {
    val allUnits: List<TacticalUnit> get() = playerUnits + enemyUnits

    companion object {
        /**
         * Collects all military units within [radius] tiles of the combat and
         * assigns them to player vs enemy sides based on [attackingUnit]'s civ.
         */
        fun buildFrom(attackingUnit: MapUnit, defendingUnit: MapUnit, radius: Int = DEFAULT_RADIUS): TacticalBattleContext {
            val centerTile = defendingUnit.getTile()
            val playerCiv = attackingUnit.civ

            val tiles = centerTile.getTilesInDistance(radius).toList()

            val playerUnits = mutableListOf<TacticalUnit>()
            val enemyUnits = mutableListOf<TacticalUnit>()

            for (tile in tiles) {
                val unit = tile.militaryUnit ?: continue
                if (unit.isCivilian()) continue
                val tacticalUnit = TacticalUnit(unit).also {
                    it.worldPos = tileToWorldPos(tile)
                    it.currentTile = tile
                }
                if (unit.civ == playerCiv) playerUnits.add(tacticalUnit)
                else if (unit.civ.isAtWarWith(playerCiv)) enemyUnits.add(tacticalUnit)
                // Neutral civs don't participate
            }

            return TacticalBattleContext(centerTile, radius, tiles, playerUnits, enemyUnits)
        }

        /**
         * Converts a tile's hex position to a world Vector2 used for actor placement.
         * Uses the same formula as HexMath so positions align with the tile map.
         */
        fun tileToWorldPos(tile: Tile): Vector2 {
            return HexMath.hex2WorldCoords(tile.position)
                .scl(TacticalUnit.HEX_SIZE)
        }

        const val DEFAULT_RADIUS = 3
    }
}
