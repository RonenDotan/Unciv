package com.unciv.ui.screens.tacticalbattle

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.battle.tactical.TacticalBattleContext
import com.unciv.logic.battle.tactical.TacticalBattleResult
import com.unciv.logic.battle.tactical.TacticalUnit
import com.unciv.logic.battle.tactical.TacticalUnitState
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Full-screen tactical battle screen.
 * Runs a real-time simulation where units automatically fight each other.
 * The player can dismiss after the battle concludes.
 */
class TacticalBattleScreen(private val context: TacticalBattleContext) : BaseScreen() {

    private val result = TacticalBattleResult(context.playerUnits, context.enemyUnits)
    private val healthLabels = mutableMapOf<TacticalUnit, Label>()
    private val resultLabel = "".toLabel(Color.GOLD, 28).apply { setAlignment(Align.center) }
    private var battleOver = false

    init {
        val root = Table()
        root.setFillParent(true)
        root.background = skinStrings.getUiBackground("General/Border", tintColor = Color(0f, 0f, 0.1f, 0.95f))
        stage.addActor(root)

        root.add("⚔ Tactical Battle".toLabel(Color.GOLD, 36)).padBottom(16f).row()
        root.add("Battle radius: ${context.radius} tiles".toLabel(Color.WHITE, 14)).padBottom(20f).row()

        root.add(buildUnitTable()).padBottom(16f).row()
        root.add(resultLabel).padBottom(20f).row()

        val dismissButton = "End Battle".toLabel(Color.WHITE, 20).apply { setAlignment(Align.center) }
        root.add(dismissButton).width(200f).height(50f).padBottom(20f)

        dismissButton.onClick { dismiss() }
        root.keyShortcuts.add(Input.Keys.ESCAPE) { dismiss() }
        root.keyShortcuts.add(Input.Keys.ENTER) { if (battleOver) dismiss() }
        root.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
    }

    private fun buildUnitTable(): Table {
        val table = Table()

        table.add("Your forces".toLabel(Color.GREEN, 16)).padRight(40f)
        table.add("Enemy forces".toLabel(Color.RED, 16)).row()

        val maxRows = maxOf(context.playerUnits.size, context.enemyUnits.size)
        for (i in 0 until maxRows) {
            val player = context.playerUnits.getOrNull(i)
            val enemy = context.enemyUnits.getOrNull(i)

            if (player != null) {
                val lbl = "${player.getName()} HP:${player.currentHealth}".toLabel(Color.GREEN, 13)
                healthLabels[player] = lbl
                table.add(lbl).left().padRight(40f)
            } else {
                table.add()
            }

            if (enemy != null) {
                val lbl = "${enemy.getName()} HP:${enemy.currentHealth}".toLabel(Color.RED, 13)
                healthLabels[enemy] = lbl
                table.add(lbl).left()
            } else {
                table.add()
            }
            table.row()
        }

        return table
    }

    private fun simulationStep(delta: Float) {
        if (battleOver) return
        val cappedDelta = delta.coerceAtMost(1f / 30f)

        for (unit in context.playerUnits) unit.update(cappedDelta, context.enemyUnits)
        for (unit in context.enemyUnits) unit.update(cappedDelta, context.playerUnits)

        // Update HP labels
        for ((unit, label) in healthLabels) {
            val state = if (unit.state == TacticalUnitState.DEAD) "DEAD" else "HP:${unit.currentHealth}"
            label.setText("${unit.getName()} $state")
        }

        // Check win condition
        if (result.playerWon) {
            resultLabel.setText("Victory! Your forces prevailed.")
            resultLabel.color = Color.GOLD
            battleOver = true
        } else if (result.enemyWon) {
            resultLabel.setText("Defeat! Your forces were overcome.")
            resultLabel.color = Color.RED
            battleOver = true
        }
    }

    override fun show() {
        super.show()
        Gdx.graphics.isContinuousRendering = true
    }

    override fun hide() {
        Gdx.graphics.isContinuousRendering = game.settings.continuousRendering
        super.hide()
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0.05f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        simulationStep(delta)
        stage.act()
        stage.draw()
    }

    private fun dismiss() {
        result.applyToGame()
        game.popScreen()
    }
}
