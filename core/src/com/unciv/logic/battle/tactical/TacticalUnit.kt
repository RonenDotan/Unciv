package com.unciv.logic.battle.tactical

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
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
class TacticalUnit(val sourceUnit: MapUnit) : ICombatant, TacticalCombatant {

    // --- Position and state ---
    override var worldPos: Vector2 = Vector2.Zero.cpy()
    var currentTile: Tile = sourceUnit.getTile()
    var state: TacticalUnitState = TacticalUnitState.IDLE
    var currentTarget: TacticalCombatant? = null  // TacticalUnit or TacticalCity

    // --- Health (independent from sourceUnit.health during battle) ---
    var currentHealth: Int = sourceUnit.health
    /** Set by TacticalBattleScreen before applyToGame() — the closest real tile to the unit's final position. */
    var finalTile: Tile? = null

    var wasHitThisFrame: Boolean = false
    var lastDamageTaken: Int = 0
    var wasAttackingThisFrame: Boolean = false
    var lastAttackTargetWorldPos: Vector2? = null

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

    /** Player-commanded move destination. Overrides AI when set. Cleared when reached. */
    var commandedDestination: Vector2? = null

    /** Player-commanded attack target. Overrides AI target selection when set. */
    var commandedTarget: TacticalCombatant? = null  // TacticalUnit or TacticalCity

    val isPlayerControlled: Boolean
        get() = sourceUnit.civ.isHuman()

    /**
     * Advances this unit's state machine by [delta] seconds.
     * [enemies] is the list of opposing TacticalUnits; [allies] is the same-side list.
     * [enemyCities] are enemy TacticalCities that can also be targeted.
     * Player commands ([commandedDestination], [commandedTarget]) take priority over AI logic.
     *
     * Movement model:
     * - Melee: advance straight toward target, blended with a separation force that steers
     *   around nearby units — naturally finds the shortest unblocked path in open space.
     * - Ranged: hold position once target is within attack range; back away if target closes
     *   inside [RANGED_MIN_DIST_FACTOR] * attackRangePixels.
     * - All units: separation repulsion from any unit within [SEP_RADIUS] prevents stacking.
     */
    fun update(delta: Float, enemies: List<TacticalUnit>, allies: List<TacticalUnit>, enemyCities: List<TacticalCity> = emptyList()) {
        if (state == TacticalUnitState.DEAD || state == TacticalUnitState.ESCAPED) return

        cooldownRemaining = (cooldownRemaining - delta).coerceAtLeast(0f)

        wasHitThisFrame = false
        wasAttackingThisFrame = false

        // Clear stale commanded target
        if (commandedTarget?.isAliveForBattle() == false) commandedTarget = null

        // Player move command: go to destination with separation, ignoring enemies
        val dest = commandedDestination
        if (dest != null) {
            val distToDest = worldPos.dst(dest)
            if (distToDest < 5f) {
                commandedDestination = null  // arrived
            } else {
                state = TacticalUnitState.MOVING
                val dir = dest.cpy().sub(worldPos).nor()
                val sep = separationForce(allies + enemies)
                val finalDir = dir.add(sep.scl(SEP_WEIGHT)).nor()
                worldPos.add(finalDir.scl(moveSpeed * delta))
                return
            }
        }

        // Combine alive enemy units and cities into a single target list
        val aliveEnemyUnits = enemies.filter { it.state != TacticalUnitState.DEAD && it.state != TacticalUnitState.ESCAPED }
        val aliveEnemyCities = enemyCities.filter { it.isAliveForBattle() }
        val aliveTargets: List<TacticalCombatant> = aliveEnemyUnits + aliveEnemyCities
        if (aliveTargets.isEmpty()) { state = TacticalUnitState.IDLE; return }

        // Determine target: player command > AI nearest-enemy (unit or city)
        val target: TacticalCombatant = commandedTarget
            ?: run {
                if (currentTarget == null || currentTarget?.isAliveForBattle() == false) {
                    currentTarget = aliveTargets.minByOrNull { it.worldPos.dst(worldPos) }
                }
                currentTarget
            } ?: return

        val dist = worldPos.dst(target.worldPos)

        if (dist <= attackRangePixels) {
            // Attack if ready
            state = TacticalUnitState.ATTACKING
            if (cooldownRemaining <= 0f) {
                val damage = when (target) {
                    is TacticalUnit -> BattleDamage.calculateDamageToDefender(this, target, currentTile, randomnessFactor = 0.5f).coerceAtLeast(1)
                    is TacticalCity -> BattleDamage.calculateDamageToDefender(MapUnitCombatant(sourceUnit), CityCombatant(target.sourceCity), currentTile, randomnessFactor = 0.5f).coerceAtLeast(1)
                    else -> 1
                }
                when (target) {
                    is TacticalUnit -> target.takeDamage(damage)
                    is TacticalCity -> target.takeDamage(damage, this)
                }
                wasAttackingThisFrame = true
                lastAttackTargetWorldPos = target.worldPos.cpy()
                cooldownRemaining = attackCooldownSeconds
            }

            // Ranged: back away if enemy is too close; melee: hold position
            if (isRangedUnit && dist < attackRangePixels * RANGED_MIN_DIST_FACTOR) {
                state = TacticalUnitState.MOVING
                val awayDir = worldPos.cpy().sub(target.worldPos).nor()
                val sep = separationForce(allies + enemies)
                val finalDir = awayDir.add(sep.scl(SEP_WEIGHT)).nor()
                worldPos.add(finalDir.scl(moveSpeed * delta))
            } else {
                // Apply only separation while standing still so units don't stack
                val sep = separationForce(allies + enemies)
                if (sep.len() > 0.1f) worldPos.add(sep.nor().scl(moveSpeed * delta * SEP_IDLE_WEIGHT))
            }
        } else {
            // Advance toward target; ranged units stop at attack range
            state = TacticalUnitState.MOVING
            if (!isRangedUnit || dist > attackRangePixels) {
                val toTarget = target.worldPos.cpy().sub(worldPos).nor()
                val sep = separationForce(allies + enemies)
                val finalDir = toTarget.add(sep.scl(SEP_WEIGHT)).nor()
                worldPos.add(finalDir.scl(moveSpeed * delta))
            }
        }
    }

    /**
     * Returns a repulsion vector pushing this unit away from all nearby units within [SEP_RADIUS].
     * Magnitude scales linearly with overlap — zero at [SEP_RADIUS], max at zero distance.
     */
    private fun separationForce(others: List<TacticalUnit>): Vector2 {
        val force = Vector2()
        for (other in others) {
            if (other === this || other.state == TacticalUnitState.DEAD) continue
            val diff = worldPos.cpy().sub(other.worldPos)
            val dist = diff.len()
            if (dist < SEP_RADIUS && dist > 0.1f) {
                force.add(diff.nor().scl((SEP_RADIUS - dist) / SEP_RADIUS))
            }
        }
        return force
    }

    // --- ICombatant implementation ---
    // Delegates strength/type queries to the source unit so BattleDamage math works correctly.
    // takeDamage() affects only this TacticalUnit's health, NOT the source MapUnit.

    override fun isAliveForBattle(): Boolean = state != TacticalUnitState.DEAD && state != TacticalUnitState.ESCAPED
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
        wasHitThisFrame = true
        lastDamageTaken = damage
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
        /**
         * Scale factor matching TileGroupMap's coordinate system:
         * TileGroupMap positions tiles at hex2WorldCoords(pos) * 0.8f * groupSize (50f) = pos * 40f.
         */
        const val HEX_SIZE = 40f  // = TileGroupMap.groupSize * 0.8f

        /** sqrt(3) — distance between adjacent hex centers in world coords at HEX_SIZE scale */
        val SQRT3 = sqrt(3.0).toFloat()

        /** Pixels-per-second per 1 movement point */
        const val SPEED_SCALE = 2f

        const val MELEE_COOLDOWN = 8f
        const val RANGED_COOLDOWN = 10f

        /** Pixel radius within which units repel each other */
        const val SEP_RADIUS = HEX_SIZE * 1.8f

        /** How strongly separation overrides the primary move direction */
        const val SEP_WEIGHT = 1.2f

        /** Separation weight applied when standing still (attacking/idle) — gentler drift */
        const val SEP_IDLE_WEIGHT = 0.25f

        /** Ranged units back away when target is closer than this fraction of their attack range */
        const val RANGED_MIN_DIST_FACTOR = 0.55f
    }
}
