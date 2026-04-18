package com.unciv.logic.battle.tactical

import com.badlogic.gdx.math.Vector2

/** Common interface for units and cities participating in a tactical battle. */
interface TacticalCombatant {
    val worldPos: Vector2
    fun isAliveForBattle(): Boolean
    fun getName(): String
}
