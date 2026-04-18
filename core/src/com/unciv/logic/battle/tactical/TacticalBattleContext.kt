package com.unciv.logic.battle.tactical

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.city.City
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
    val enemyUnits: List<TacticalUnit>,
    val enemyCities: List<TacticalCity> = emptyList(),
    /** True when the battle was initiated by a unit attacking a city directly. */
    val primaryTargetIsCity: Boolean = false
) {
    val allUnits: List<TacticalUnit> get() = playerUnits + enemyUnits

    companion object {
        /**
         * Collects all military units within [radius] tiles of the combat and
         * assigns them to player vs enemy sides based on [attackingUnit]'s civ.
         * Also includes enemy city centers within range.
         */
        fun buildFrom(attackingUnit: MapUnit, defendingUnit: MapUnit, radius: Int = DEFAULT_RADIUS): TacticalBattleContext {
            val centerTile = defendingUnit.getTile()
            val playerCiv = attackingUnit.civ

            val tiles = centerTile.getTilesInDistance(radius).toList()

            val playerUnits = mutableListOf<TacticalUnit>()
            val enemyUnits = mutableListOf<TacticalUnit>()
            val enemyCities = mutableListOf<TacticalCity>()

            for (tile in tiles) {
                if (tile.isCityCenter()) {
                    val city = tile.getCity() ?: continue
                    if (city.civ.isAtWarWith(playerCiv)) enemyCities.add(TacticalCity(city))
                    continue
                }
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

            return TacticalBattleContext(centerTile, radius, tiles, playerUnits, enemyUnits, enemyCities, primaryTargetIsCity = false)
        }

        /**
         * Builds a context for a battle initiated by a unit attacking a city directly.
         * The [defendingCity] is always the primary target; battle ends when it's captured or all attackers die.
         */
        fun buildFrom(attackingUnit: MapUnit, defendingCity: City, radius: Int = DEFAULT_RADIUS): TacticalBattleContext {
            val centerTile = defendingCity.getCenterTile()
            val playerCiv = attackingUnit.civ

            val tiles = centerTile.getTilesInDistance(radius).toList()

            val playerUnits = mutableListOf<TacticalUnit>()
            val enemyUnits = mutableListOf<TacticalUnit>()
            val enemyCities = mutableListOf(TacticalCity(defendingCity))

            for (tile in tiles) {
                if (tile.isCityCenter()) {
                    if (tile != centerTile) {
                        val city = tile.getCity() ?: continue
                        if (city.civ.isAtWarWith(playerCiv)) enemyCities.add(TacticalCity(city))
                    }
                    continue
                }
                val unit = tile.militaryUnit ?: continue
                if (unit.isCivilian()) continue
                val tacticalUnit = TacticalUnit(unit).also {
                    it.worldPos = tileToWorldPos(tile)
                    it.currentTile = tile
                }
                if (unit.civ == playerCiv) playerUnits.add(tacticalUnit)
                else if (unit.civ.isAtWarWith(playerCiv)) enemyUnits.add(tacticalUnit)
            }

            return TacticalBattleContext(centerTile, radius, tiles, playerUnits, enemyUnits, enemyCities, primaryTargetIsCity = true)
        }

        /**
         * Converts a tile's hex position to a world Vector2 matching TileGroupMap's coordinate system.
         * TileGroupMap places tiles at: hex2WorldCoords(pos) * 0.8f * groupSize (50f) = pos * 40f.
         */
        fun tileToWorldPos(tile: Tile): Vector2 {
            return HexMath.hex2WorldCoords(tile.position)
                .scl(TacticalUnit.HEX_SIZE)  // HEX_SIZE = 40f = TileGroupMap.groupSize * 0.8f
        }

        const val DEFAULT_RADIUS = 3
    }
}
