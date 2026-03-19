package com.unciv.logic.battle.tactical

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.UnitType

enum class TacticalUnitState { IDLE, MOVING, ATTACKING, DEAD, ESCAPED }

/**
 * Represents a [MapUnit] participating in a tactical battle.
 * Holds a mutable snapshot of the unit's state during the battle.
 * The original [sourceUnit] is NOT modified until [TacticalBattleResult] is applied.
 */
class TacticalUnit(val sourceUnit: MapUnit) : ICombatant {

    // --- Position and state ---
    var worldPos: Vector2 = Vector2.Zero.cpy()
    var currentTile: Tile = sourceUnit.getTile()
    var state: TacticalUnitState = TacticalUnitState.IDLE
    var currentTarget: TacticalUnit? = null

    // --- Health (independent from sourceUnit.health during battle) ---
    var health: Int = sourceUnit.health

    // --- Derived combat stats ---
    val isRangedUnit: Boolean = sourceUnit.baseUnit.isRanged()
    val attackRangeTiles: Int = if (isRangedUnit) sourceUnit.baseUnit.range else 1

    /** Pixels per second, scaled from base movement stat */
    val moveSpeed: Float = sourceUnit.baseUnit.movement * SPEED_SCALE

    /** Attack range in world pixels */
    val attackRangePixels: Float = attackRangeTiles * HEX_SIZE

    /** Seconds between attacks */
    val attackCooldownSeconds: Float = if (isRangedUnit) RANGED_COOLDOWN else MELEE_COOLDOWN

    var cooldownRemaining: Float = 0f

    val isPlayerControlled: Boolean
        get() = sourceUnit.civ.isHuman()

    // --- ICombatant implementation ---
    // Delegates strength/type queries to the source unit so BattleDamage math works correctly.
    // takeDamage() affects only this TacticalUnit's health, NOT the source MapUnit.

    override fun getName(): String = sourceUnit.name
    override fun getHealth(): Int = health
    override fun getMaxHealth(): Int = 100
    override fun getUnitType(): UnitType = sourceUnit.type
    override fun getCivInfo(): Civilization = sourceUnit.civ
    override fun getTile(): Tile = currentTile
    override fun isDefeated(): Boolean = health <= 0
    override fun isInvisible(to: Civilization): Boolean = sourceUnit.isInvisible(to)
    override fun canAttack(): Boolean = cooldownRemaining <= 0f && state != TacticalUnitState.DEAD
    override fun matchesFilter(filter: String, multiFilter: Boolean): Boolean =
        sourceUnit.matchesFilter(filter, multiFilter)
    override fun getAttackSound(): UncivSound = MapUnitCombatant(sourceUnit).getAttackSound()

    override fun takeDamage(damage: Int) {
        health = (health - damage).coerceAtLeast(0)
        if (health <= 0) state = TacticalUnitState.DEAD
    }

    override fun getAttackingStrength(defender: ICombatant?): Int =
        MapUnitCombatant(sourceUnit).getAttackingStrength(defender)

    override fun getDefendingStrength(attacker: ICombatant?): Int =
        MapUnitCombatant(sourceUnit).getDefendingStrength(attacker)

    override fun isRanged(): Boolean = isRangedUnit
    override fun isAirUnit(): Boolean = sourceUnit.baseUnit.isAirUnit()
    override fun isWaterUnit(): Boolean = sourceUnit.baseUnit.isWaterUnit
    override fun isLandUnit(): Boolean = sourceUnit.baseUnit.isLandUnit

    companion object {
        /** World pixels per tile — must match actual TileGroupMap tile size at 1x zoom */
        const val HEX_SIZE = 60f

        /** Pixels-per-second per 1 movement point */
        const val SPEED_SCALE = 25f

        const val MELEE_COOLDOWN = 1.5f
        const val RANGED_COOLDOWN = 2.0f
    }
}
