package com.unciv.logic.battle.tactical

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.UnitType
import kotlin.math.sqrt

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
    var currentHealth: Int = sourceUnit.health

    // --- Derived combat stats ---
    val isRangedUnit: Boolean = sourceUnit.baseUnit.isRanged()
    val attackRangeTiles: Int = if (isRangedUnit) sourceUnit.baseUnit.range else 1

    /** Pixels per second, scaled from base movement stat */
    val moveSpeed: Float = sourceUnit.baseUnit.movement * SPEED_SCALE

    /**
     * Attack range in world pixels.
     * Adjacent hex tile centers are HEX_SIZE * sqrt(3) apart after coordinate scaling,
     * so range in tiles must be multiplied by that same factor.
     */
    val attackRangePixels: Float = attackRangeTiles * HEX_SIZE * SQRT3

    /** Seconds between attacks */
    val attackCooldownSeconds: Float = if (isRangedUnit) RANGED_COOLDOWN else MELEE_COOLDOWN

    var cooldownRemaining: Float = 0f

    val isPlayerControlled: Boolean
        get() = sourceUnit.civ.isHuman()

    /**
     * Advances this unit's state machine by [delta] seconds.
     * [enemies] is the list of opposing TacticalUnits.
     */
    fun update(delta: Float, enemies: List<TacticalUnit>) {
        if (state == TacticalUnitState.DEAD || state == TacticalUnitState.ESCAPED) return

        cooldownRemaining = (cooldownRemaining - delta).coerceAtLeast(0f)

        val aliveEnemies = enemies.filter {
            it.state != TacticalUnitState.DEAD && it.state != TacticalUnitState.ESCAPED
        }
        if (aliveEnemies.isEmpty()) {
            state = TacticalUnitState.IDLE
            return
        }

        // Re-target if needed
        if (currentTarget == null ||
            currentTarget!!.state == TacticalUnitState.DEAD ||
            currentTarget!!.state == TacticalUnitState.ESCAPED) {
            currentTarget = aliveEnemies.minByOrNull { it.worldPos.dst(worldPos) }
        }

        val target = currentTarget ?: return
        val dist = worldPos.dst(target.worldPos)

        if (dist <= attackRangePixels) {
            state = TacticalUnitState.ATTACKING
            if (cooldownRemaining <= 0f) {
                val damage = BattleDamage.calculateDamageToDefender(
                    this, target, currentTile, randomnessFactor = 0.5f
                ).coerceAtLeast(1)
                target.takeDamage(damage)
                cooldownRemaining = attackCooldownSeconds
            }
        } else {
            state = TacticalUnitState.MOVING
            val dir = target.worldPos.cpy().sub(worldPos).nor()
            worldPos.add(dir.scl(moveSpeed * delta))
        }
    }

    // --- ICombatant implementation ---
    // Delegates strength/type queries to the source unit so BattleDamage math works correctly.
    // takeDamage() affects only this TacticalUnit's health, NOT the source MapUnit.

    override fun getName(): String = sourceUnit.name
    override fun getHealth(): Int = currentHealth
    override fun getMaxHealth(): Int = 100
    override fun getUnitType(): UnitType = sourceUnit.type
    override fun getCivInfo(): Civilization = sourceUnit.civ
    override fun getTile(): Tile = currentTile
    override fun isDefeated(): Boolean = currentHealth <= 0
    override fun isInvisible(to: Civilization): Boolean = sourceUnit.isInvisible(to)
    override fun canAttack(): Boolean = cooldownRemaining <= 0f && state != TacticalUnitState.DEAD
    override fun matchesFilter(filter: String, multiFilter: Boolean): Boolean =
        sourceUnit.matchesFilter(filter, multiFilter)
    override fun getAttackSound(): UncivSound = MapUnitCombatant(sourceUnit).getAttackSound()

    override fun takeDamage(damage: Int) {
        currentHealth = (currentHealth - damage).coerceAtLeast(0)
        if (currentHealth <= 0) state = TacticalUnitState.DEAD
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
        /** World pixels per tile unit (before hex coordinate scaling) */
        const val HEX_SIZE = 60f

        /** sqrt(3) — distance between adjacent hex centers in world coords at HEX_SIZE scale */
        val SQRT3 = sqrt(3.0).toFloat()

        /** Pixels-per-second per 1 movement point */
        const val SPEED_SCALE = 25f

        const val MELEE_COOLDOWN = 1.5f
        const val RANGED_COOLDOWN = 2.0f
    }
}
