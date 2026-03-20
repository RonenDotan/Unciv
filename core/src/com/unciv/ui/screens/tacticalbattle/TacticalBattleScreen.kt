package com.unciv.ui.screens.tacticalbattle

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.battle.tactical.TacticalBattleContext
import com.unciv.logic.battle.tactical.TacticalBattleResult
import com.unciv.logic.battle.tactical.TacticalUnit
import com.unciv.logic.battle.tactical.TacticalUnitState
import com.unciv.models.UncivSound
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Full-screen tactical battle screen with real-time hex map, moving unit sprites,
 * and player input (click to select, click to move/attack, speed controls).
 */
class TacticalBattleScreen(private val context: TacticalBattleContext) : BaseScreen() {

    private val result = TacticalBattleResult(context.playerUnits, context.enemyUnits)
    private val healthLabels = mutableMapOf<TacticalUnit, Label>()
    private val resultLabel = "".toLabel(Color.GOLD, 24).apply { setAlignment(Align.center) }
    private val unitActors = mutableMapOf<TacticalUnit, TacticalUnitActor>()
    private val mapHolder = TacticalMapHolder(context)
    private var battleOver = false
    private var battleStarted = false
    private var speedMultiplier = 0f  // starts paused
    private var selectedUnit: TacticalUnit? = null
    private var startButton: Label? = null

    init {
        val mapTable = Table()
        mapTable.add(mapHolder).grow().row()
        mapTable.setFillParent(true)
        stage.addActor(mapTable)

        val hud = buildHud()
        hud.setFillParent(true)
        stage.addActor(hud)

        // Centered "Start Battle" overlay — hidden once clicked
        val startOverlay = Table()
        startOverlay.setFillParent(true)
        val btnTable = Table()
        btnTable.background = skinStrings.getUiBackground("General/Border", tintColor = Color(0f, 0f, 0f, 0.85f))
        val btn = "⚔  Start Battle  ⚔".toLabel(Color.GOLD, 28).apply { setAlignment(Align.center) }
        startButton = btn
        btnTable.add(btn).pad(20f)
        btnTable.onClick(UncivSound.Silent) {
            battleStarted = true
            speedMultiplier = 1f
            startOverlay.remove()
        }
        startOverlay.add(btnTable).pad(20f)
        stage.addActor(startOverlay)

        // Sync worldPos to actual tile group positions, then create actors
        for (unit in context.allUnits) {
            mapHolder.getWorldPos(unit.currentTile)?.let { unit.worldPos.set(it) }
            val actor = TacticalUnitActor(unit, mapHolder.tileSetStrings) { onUnitClicked(it) }
            unitActors[unit] = actor
            mapHolder.unitLayer.addActor(actor)
            actor.centerOn(unit.worldPos.x, unit.worldPos.y)
        }

        // Background click on the map: move selected unit to that position
        mapHolder.unitLayer.touchable = Touchable.enabled
        mapHolder.unitLayer.addListener(object : InputListener() {
            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                val sel = selectedUnit ?: return false
                if (!sel.isPlayerControlled || sel.state == TacticalUnitState.DEAD) return false
                sel.commandedDestination = Vector2(x, y)
                sel.commandedTarget = null
                return true
            }
        })
    }

    private fun onUnitClicked(actor: TacticalUnitActor) {
        val unit = actor.tacticalUnit
        if (unit.state == TacticalUnitState.DEAD) return

        val sel = selectedUnit
        if (unit.isPlayerControlled) {
            // Select this friendly unit
            selectedUnit?.let { unitActors[it]?.isSelected = false }
            selectedUnit = if (sel == unit) null else unit  // toggle deselect
            actor.isSelected = selectedUnit == unit
        } else {
            // Enemy clicked: command selected unit to attack it
            if (sel != null && sel.isPlayerControlled && sel.state != TacticalUnitState.DEAD) {
                sel.commandedTarget = unit
                sel.commandedDestination = null
            }
        }
    }

    private fun buildHud(): Table {
        val hud = Table()
        hud.touchable = Touchable.childrenOnly

        val bottomPanel = Table()
        bottomPanel.background = skinStrings.getUiBackground(
            "General/Border", tintColor = Color(0f, 0f, 0f, 0.75f)
        )
        bottomPanel.pad(8f)

        // Speed buttons row
        val speedRow = Table()
        for ((label, speed) in listOf("⏸" to 0f, "▶" to 1f, "▶▶" to 2f)) {
            val btn = label.toLabel(Color.WHITE, 18).apply { setAlignment(Align.center) }
            btn.onClick(UncivSound.Silent) { if (battleStarted) speedMultiplier = speed }
            speedRow.add(btn).width(50f).height(30f).padRight(8f)
        }
        bottomPanel.add(speedRow).colspan(2).padBottom(4f).row()
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

        hud.add().grow().row()
        hud.add(bottomPanel).growX().row()

        hud.keyShortcuts.add(Input.Keys.ESCAPE) { dismiss() }
        hud.keyShortcuts.add(Input.Keys.ENTER) { if (battleOver) dismiss() }
        hud.keyShortcuts.add(Input.Keys.SPACE) { if (battleStarted) speedMultiplier = if (speedMultiplier == 0f) 1f else 0f }

        return hud
    }

    private fun simulationStep(delta: Float) {
        if (battleOver || speedMultiplier == 0f) return
        val cappedDelta = (delta * speedMultiplier).coerceAtMost(1f / 20f)

        for (unit in context.playerUnits) unit.update(cappedDelta, context.enemyUnits)
        for (unit in context.enemyUnits) unit.update(cappedDelta, context.playerUnits)

        for ((unit, actor) in unitActors) {
            actor.centerOn(unit.worldPos.x, unit.worldPos.y)
            actor.updateFromUnit(cappedDelta)
        }
        for ((unit, label) in healthLabels) {
            val state = if (unit.state == TacticalUnitState.DEAD) "DEAD" else "HP:${unit.currentHealth}"
            label.setText("${unit.getName()} $state")
        }

        when {
            result.playerWon -> { resultLabel.setText("Victory!"); resultLabel.color = Color.GOLD; battleOver = true }
            result.enemyWon -> { resultLabel.setText("Defeat!"); resultLabel.color = Color.RED; battleOver = true }
        }
    }

    override fun show() {
        super.show()
        Gdx.graphics.isContinuousRendering = true
        Gdx.app.postRunnable {
            stage.act(0f)  // force table layout so mapHolder gets its actual width/height
            val mapContent = mapHolder.actor
            if (mapContent != null && mapHolder.width > 0f && mapHolder.height > 0f) {
                val zoomX = mapHolder.width * 1.8f / mapContent.width
                val zoomY = mapHolder.height * 1.8f / mapContent.height
                val targetZoom = minOf(zoomX, zoomY).coerceIn(mapHolder.minZoom, mapHolder.maxZoom)
                mapHolder.zoom(targetZoom)
            }
            mapHolder.centerOnTile(context.centerTile)
        }
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
