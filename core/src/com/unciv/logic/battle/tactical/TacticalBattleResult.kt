package com.unciv.logic.battle.tactical

import com.unciv.logic.map.mapunit.MapUnit

/**
 * The outcome of a tactical battle, produced when the battle screen closes.
 * Applied to the main game state via [applyToGame].
 */
class TacticalBattleResult(
    val playerUnits: List<TacticalUnit>,
    val enemyUnits: List<TacticalUnit>
) {
    val allUnits: List<TacticalUnit> get() = playerUnits + enemyUnits

    val killedPlayerUnits: List<MapUnit>
        get() = playerUnits.filter { it.state == TacticalUnitState.DEAD }.map { it.sourceUnit }

    val killedEnemyUnits: List<MapUnit>
        get() = enemyUnits.filter { it.state == TacticalUnitState.DEAD }.map { it.sourceUnit }

    val escapedPlayerUnits: List<MapUnit>
        get() = playerUnits.filter { it.state == TacticalUnitState.ESCAPED }.map { it.sourceUnit }

    val escapedEnemyUnits: List<MapUnit>
        get() = enemyUnits.filter { it.state == TacticalUnitState.ESCAPED }.map { it.sourceUnit }

    val playerWon: Boolean
        get() = enemyUnits.all { it.state == TacticalUnitState.DEAD || it.state == TacticalUnitState.ESCAPED }

    val enemyWon: Boolean
        get() = playerUnits.all { it.state == TacticalUnitState.DEAD || it.state == TacticalUnitState.ESCAPED }

    /**
     * Writes battle results back to the main game state:
     * - Destroys killed units
     * - Copies health back to survivors
     * - Zeros out movement for all participants (they used their turn)
     */
    fun applyToGame() {
        for (tacticalUnit in allUnits) {
            val unit = tacticalUnit.sourceUnit
            when (tacticalUnit.state) {
                TacticalUnitState.DEAD -> unit.destroy()
                else -> {
                    unit.health = tacticalUnit.health.coerceAtLeast(1)
                }
            }
            // All participants expend their remaining movement and attacks
            if (tacticalUnit.state != TacticalUnitState.DEAD) {
                unit.currentMovement = 0f
                unit.attacksThisTurn = unit.maxAttacksPerTurn()
            }
        }
    }
}
