package com.unciv.logic.battle.tactical

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.models.UncivSound

enum class TacticalCityState { ALIVE, CAPTURED }

/**
 * A [City] participating in a tactical battle.
 * Stationary — placed on its center tile, never moves.
 * Bombards enemy units within range each cooldown cycle.
 * When HP reaches 0 it is flagged CAPTURED; [killedBy] records which unit dealt the killing blow.
 */
class TacticalCity(val sourceCity: City) : TacticalCombatant {

    override var worldPos: Vector2 = TacticalBattleContext.tileToWorldPos(sourceCity.getCenterTile())
    val currentTile = sourceCity.getCenterTile()

    var state: TacticalCityState = TacticalCityState.ALIVE
    var currentHealth: Int = sourceCity.health
    val maxHealth: Int = sourceCity.getMaxHealth()
    var killedBy: TacticalUnit? = null
    var cooldownRemaining: Float = 0f

    var wasAttackingThisFrame: Boolean = false
    var wasHitThisFrame: Boolean = false
    var lastDamageTaken: Int = 0
    var lastAttackTargetWorldPos: Vector2? = null

    // Cities bombard at range 2 tiles
    val attackRangePixels: Float = 2 * TacticalUnit.HEX_SIZE * TacticalUnit.SQRT3
    val attackCooldownSeconds: Float = 8f

    override fun isAliveForBattle() = state == TacticalCityState.ALIVE
    override fun getName() = sourceCity.name

    fun update(delta: Float, enemies: List<TacticalUnit>) {
        if (!isAliveForBattle()) return

        wasAttackingThisFrame = false
        wasHitThisFrame = false
        cooldownRemaining = (cooldownRemaining - delta).coerceAtLeast(0f)

        // Use our own cooldown system instead of canBombard() — that checks attackedThisTurn
        // which may already be true when the battle starts.
        val target = enemies
            .filter { it.state != TacticalUnitState.DEAD && it.state != TacticalUnitState.ESCAPED }
            .filter { worldPos.dst(it.worldPos) <= attackRangePixels }
            .minByOrNull { worldPos.dst(it.worldPos) } ?: return

        if (cooldownRemaining <= 0f) {
            val cityCombatant = CityCombatant(sourceCity)
            val damage = BattleDamage.calculateDamageToDefender(
                cityCombatant, MapUnitCombatant(target.sourceUnit), currentTile, randomnessFactor = 0.5f
            ).coerceAtLeast(1)
            target.takeDamage(damage)
            wasAttackingThisFrame = true
            lastAttackTargetWorldPos = target.worldPos.cpy()
            cooldownRemaining = attackCooldownSeconds
        }
    }

    fun takeDamage(damage: Int, attacker: TacticalUnit) {
        currentHealth = (currentHealth - damage).coerceAtLeast(0)
        wasHitThisFrame = true
        lastDamageTaken = damage
        if (currentHealth <= 0) {
            state = TacticalCityState.CAPTURED
            killedBy = attacker
        }
    }

    fun getAttackSound() = UncivSound.Bombard
}
