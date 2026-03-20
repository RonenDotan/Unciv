package com.unciv.logic.battle.tactical

import com.unciv.logic.map.tile.Tile

/**
 * The outcome of a tactical battle, produced when the battle screen closes.
 * Applied to the main game state via [applyToGame].
 */
class TacticalBattleResult(private val context: TacticalBattleContext) {

    val playerUnits get() = context.playerUnits
    val enemyUnits  get() = context.enemyUnits
    val allUnits    get() = context.allUnits

    val playerWon: Boolean
        get() = enemyUnits.all { it.state == TacticalUnitState.DEAD || it.state == TacticalUnitState.ESCAPED }

    val enemyWon: Boolean
        get() = playerUnits.all { it.state == TacticalUnitState.DEAD || it.state == TacticalUnitState.ESCAPED }

    /**
     * Writes battle results back to the main game state:
     * - Repositions survivors to the nearest battle tile matching their final worldPos
     * - Copies health back to survivors
     * - Destroys killed units
     * - Zeros out movement/attacks for all participants
     */
    fun applyToGame() {
        val usedTiles = mutableSetOf<Tile>()

        val survivors = allUnits.filter { it.state != TacticalUnitState.DEAD && it.state != TacticalUnitState.ESCAPED }
        val dead      = allUnits.filter { it.state == TacticalUnitState.DEAD }

        // Reposition survivors to the nearest available battle tile
        for (tacticalUnit in survivors) {
            val unit = tacticalUnit.sourceUnit
            unit.health = tacticalUnit.currentHealth.coerceAtLeast(1)

            // finalTile is resolved in TacticalBattleScreen using mapHolder coordinates
            // so both sides are in the same coordinate space
            val targetTile = tacticalUnit.finalTile
            val nearestTile = if (targetTile != null && targetTile !in usedTiles && unit.movement.canMoveTo(targetTile))
                targetTile
            else
                context.tiles
                    .filter { it !in usedTiles && unit.movement.canMoveTo(it) }
                    .minByOrNull { TacticalBattleContext.tileToWorldPos(it).dst(tacticalUnit.worldPos) }

            if (nearestTile != null) {
                usedTiles.add(nearestTile)
                if (nearestTile != unit.getTile()) {
                    unit.removeFromTile()
                    unit.putInTile(nearestTile)
                }
            }

            unit.currentMovement = 0f
            unit.attacksThisTurn = unit.maxAttacksPerTurn()
        }

        for (tacticalUnit in dead) {
            tacticalUnit.sourceUnit.destroy()
        }
    }
}
