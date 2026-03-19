package com.unciv.ui.screens.tacticalbattle

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Touchable
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
 * Full-screen tactical battle screen with a real-time hex map and moving unit icons.
 */
class TacticalBattleScreen(private val context: TacticalBattleContext) : BaseScreen() {

    private val result = TacticalBattleResult(context.playerUnits, context.enemyUnits)
    private val healthLabels = mutableMapOf<TacticalUnit, Label>()
    private val resultLabel = "".toLabel(Color.GOLD, 24).apply { setAlignment(Align.center) }
    private val unitActors = mutableMapOf<TacticalUnit, TacticalUnitActor>()
    private val mapHolder = TacticalMapHolder(context)
    private var battleOver = false

    init {
        // Map fills the top portion of the screen
        val mapTable = Table()
        mapTable.add(mapHolder).grow().row()
        mapTable.setFillParent(true)
        stage.addActor(mapTable)

        // HUD overlay at the bottom
        val hud = buildHud()
        hud.setFillParent(true)
        stage.addActor(hud)

        // Sync unit worldPos to actual TileGroupMap positions (which have an internal offset)
        // then create actors at those corrected positions
        for (unit in context.allUnits) {
            mapHolder.getWorldPos(unit.currentTile)?.let { unit.worldPos.set(it) }
            val actor = TacticalUnitActor(unit, mapHolder.tileSetStrings)
            unitActors[unit] = actor
            mapHolder.unitLayer.addActor(actor)
            actor.centerOn(unit.worldPos.x, unit.worldPos.y)
        }
    }

    private fun buildHud(): Table {
        val hud = Table()
        hud.touchable = Touchable.childrenOnly

        // Bottom panel: result label + unit HP + dismiss button
        val bottomPanel = Table()
        bottomPanel.background = skinStrings.getUiBackground(
            "General/Border", tintColor = Color(0f, 0f, 0f, 0.75f)
        )
        bottomPanel.pad(8f)

        bottomPanel.add(resultLabel).colspan(2).padBottom(4f).row()

        // Unit HP columns
        val playerCol = Table()
        val enemyCol = Table()
        for (unit in context.playerUnits) {
            val lbl = "${unit.getName()} HP:${unit.currentHealth}".toLabel(Color.GREEN, 11)
            healthLabels[unit] = lbl
            playerCol.add(lbl).left().row()
        }
        for (unit in context.enemyUnits) {
            val lbl = "${unit.getName()} HP:${unit.currentHealth}".toLabel(Color.RED, 11)
            healthLabels[unit] = lbl
            enemyCol.add(lbl).left().row()
        }
        bottomPanel.add(playerCol).padRight(20f).top()
        bottomPanel.add(enemyCol).top().row()

        val dismissButton = "End Battle".toLabel(Color.WHITE, 16).apply { setAlignment(Align.center) }
        bottomPanel.add(dismissButton).colspan(2).padTop(6f).width(160f).height(36f)
        dismissButton.onClick { dismiss() }

        hud.add().grow().row()  // push panel to bottom
        hud.add(bottomPanel).growX().row()

        hud.keyShortcuts.add(Input.Keys.ESCAPE) { dismiss() }
        hud.keyShortcuts.add(Input.Keys.ENTER) { if (battleOver) dismiss() }

        return hud
    }

    private fun simulationStep(delta: Float) {
        if (battleOver) return
        val cappedDelta = delta.coerceAtMost(1f / 30f)

        for (unit in context.playerUnits) unit.update(cappedDelta, context.enemyUnits)
        for (unit in context.enemyUnits) unit.update(cappedDelta, context.playerUnits)

        // Update actors
        for ((unit, actor) in unitActors) {
            actor.centerOn(unit.worldPos.x, unit.worldPos.y)
            actor.updateFromUnit()
        }

        // Update HP labels
        for ((unit, label) in healthLabels) {
            val state = if (unit.state == TacticalUnitState.DEAD) "DEAD" else "HP:${unit.currentHealth}"
            label.setText("${unit.getName()} $state")
        }

        // Check win condition
        when {
            result.playerWon -> {
                resultLabel.setText("Victory!")
                resultLabel.color = Color.GOLD
                battleOver = true
            }
            result.enemyWon -> {
                resultLabel.setText("Defeat!")
                resultLabel.color = Color.RED
                battleOver = true
            }
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
