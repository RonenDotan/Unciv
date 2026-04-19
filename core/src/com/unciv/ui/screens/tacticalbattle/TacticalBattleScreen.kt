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
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.unciv.logic.battle.tactical.TacticalBattleContext
import com.unciv.logic.battle.tactical.TacticalBattleResult
import com.unciv.logic.battle.tactical.TacticalCity
import com.unciv.logic.battle.tactical.TacticalCityState
import com.unciv.logic.battle.tactical.TacticalUnit
import com.unciv.logic.battle.tactical.TacticalUnitState
import com.unciv.models.UncivSound
import com.unciv.ui.components.extensions.toLabel
import com.unciv.models.translations.tr
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Full-screen tactical battle screen with real-time hex map, moving unit sprites,
 * and player input (click to select, click to move/attack, speed controls).
 */
class TacticalBattleScreen(
    private val context: TacticalBattleContext,
    private val onDismiss: (() -> Unit)? = null
) : BaseScreen() {

    private val result = TacticalBattleResult(context)
    private val spriteSize = com.unciv.ui.components.tilegroups.TileGroupMap.groupSize * 1.5f
    private val VICTORY_DELAY = 0.8f
    private val DEPLOY_SCALE = 3.0f
    private val healthLabels = mutableMapOf<TacticalUnit, Label>()
    private val cityHealthLabels = mutableMapOf<TacticalCity, Label>()
    private val resultLabel = "".toLabel(Color.GOLD, 24).apply { setAlignment(Align.center) }
    private val unitActors = mutableMapOf<TacticalUnit, TacticalUnitActor>()
    private val cityActors = mutableMapOf<TacticalCity, TacticalCityActor>()
    private val mapHolder = TacticalMapHolder(context)
    private var battleCenterWorldPos: Vector2? = null
    private val escapeRadiusPx = (context.radius - 0.5f) * TacticalUnit.HEX_SIZE * TacticalUnit.SQRT3
    private var battleOver = false
    private var battleStarted = false
    private var victoryDelayTimer = -1f  // counts down after all enemies/player units die
    private var speedMultiplier = 0f  // starts paused
    private var selectedUnit: TacticalUnit? = null
    private var startButton: Label? = null
    private var zoomSlider: Slider? = null
    private var speedSlider: Slider? = null
    private var updatingSlider = false
    private var updatingSpeedSlider = false

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
            updatingSpeedSlider = true
            speedSlider?.value = 1f
            updatingSpeedSlider = false
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

        // Create city actors for enemy cities in range; sync worldPos to mapHolder coordinate space
        for (city in context.enemyCities) {
            mapHolder.getWorldPos(city.currentTile)?.let { city.worldPos.set(it) }
            val actor = TacticalCityActor(city)
            cityActors[city] = actor
            mapHolder.unitLayer.addActor(actor)
            actor.centerOn(city.worldPos.x, city.worldPos.y)
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

        // Speed slider + Zoom slider on the same row
        val spdSlider = Slider(0f, 3f, 0.1f, false, skin)
        spdSlider.value = speedMultiplier
        spdSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor) {
                if (!updatingSpeedSlider && battleStarted) speedMultiplier = spdSlider.value
            }
        })
        speedSlider = spdSlider

        val zoomSliderWidget = Slider(mapHolder.minZoom, mapHolder.maxZoom, 0.1f, false, skin)
        zoomSliderWidget.value = mapHolder.scaleX
        zoomSliderWidget.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor) {
                if (!updatingSlider) mapHolder.zoom(zoomSliderWidget.value)
            }
        })
        zoomSlider = zoomSliderWidget

        val slidersRow = Table()
        slidersRow.add("Spd".toLabel(Color.LIGHT_GRAY, 12)).padRight(4f)
        slidersRow.add(spdSlider).width(150f).height(24f).padRight(20f)
        slidersRow.add("Zoom".toLabel(Color.LIGHT_GRAY, 12)).padRight(4f)
        slidersRow.add(zoomSliderWidget).width(150f).height(24f)
        bottomPanel.add(slidersRow).colspan(2).padBottom(6f).row()
        bottomPanel.add(resultLabel).colspan(2).padBottom(4f).row()

        // Unit HP columns
        val playerCol = Table()
        val enemyCol = Table()
        for (unit in context.playerUnits) {
            val lbl = "${unit.getName()}  ${unit.currentHealth}hp".toLabel(Color.GREEN, 12)
            healthLabels[unit] = lbl
            playerCol.add(lbl).left().padBottom(1f).row()
        }
        for (unit in context.enemyUnits) {
            val lbl = "${unit.getName()}  ${unit.currentHealth}hp".toLabel(Color.RED, 12)
            healthLabels[unit] = lbl
            enemyCol.add(lbl).left().padBottom(1f).row()
        }
        for (city in context.enemyCities) {
            val lbl = "${city.getName()}  ${city.currentHealth}hp".toLabel(Color.ORANGE, 12)
            cityHealthLabels[city] = lbl
            enemyCol.add(lbl).left().padBottom(1f).row()
        }
        bottomPanel.add(playerCol).padRight(24f).top()
        bottomPanel.add(enemyCol).top().row()

        val dismissButton = "End Battle".toLabel(Color.WHITE, 16).apply { setAlignment(Align.center) }
        bottomPanel.add(dismissButton).colspan(2).padTop(6f).width(160f).height(36f)
        dismissButton.onClick { dismiss() }

        hud.add().grow().row()
        hud.add(bottomPanel).growX().row()

        hud.keyShortcuts.add(Input.Keys.ESCAPE) { dismiss() }
        hud.keyShortcuts.add(Input.Keys.ENTER) { if (battleOver) dismiss() }
        hud.keyShortcuts.add(Input.Keys.SPACE) {
            if (battleStarted) {
                speedMultiplier = if (speedMultiplier == 0f) 1f else 0f
                updatingSpeedSlider = true
                speedSlider?.value = speedMultiplier
                updatingSpeedSlider = false
            }
        }

        return hud
    }

    private fun simulationStep(delta: Float) {
        if (battleOver || speedMultiplier == 0f) return
        val cappedDelta = (delta * speedMultiplier).coerceAtMost(1f / 20f)

        // Check escape BEFORE update so AI doesn't pull units back on the same frame
        val center = battleCenterWorldPos
        if (center != null) {
            for (unit in context.allUnits) {
                if (unit.state == TacticalUnitState.DEAD || unit.state == TacticalUnitState.ESCAPED) continue
                if (unit.worldPos.dst(center) > escapeRadiusPx) {
                    unit.state = TacticalUnitState.ESCAPED
                    mapHolder.unitLayer.addActor(FloatingTextActor(-1, unit.worldPos.x, unit.worldPos.y + 40f, "fled".tr()))
                }
            }
        }

        for (unit in context.playerUnits) unit.update(cappedDelta, context.enemyUnits, context.playerUnits, context.enemyCities)
        for (unit in context.enemyUnits) unit.update(cappedDelta, context.playerUnits, context.enemyUnits)
        for (city in context.enemyCities) city.update(cappedDelta, context.playerUnits)

        // Spawn visual effects from flags set during update()
        for (unit in context.allUnits) {
            if (unit.wasHitThisFrame) {
                mapHolder.unitLayer.addActor(FloatingTextActor(unit.lastDamageTaken, unit.worldPos.x, unit.worldPos.y + 40f))
                if (unit.state == TacticalUnitState.DEAD)
                    mapHolder.unitLayer.addActor(DeathEffectActor(unit.worldPos.x, unit.worldPos.y, spriteSize * 1.2f))
            }
            if (unit.wasAttackingThisFrame) {
                SoundPlayer.play(unit.getAttackSound())
                unit.lastAttackTargetWorldPos?.let { target ->
                    mapHolder.unitLayer.addActor(AttackLineActor(unit.worldPos.x, unit.worldPos.y, target.x, target.y))
                }
            }
        }
        for (city in context.enemyCities) {
            if (city.wasHitThisFrame) {
                mapHolder.unitLayer.addActor(FloatingTextActor(city.lastDamageTaken, city.worldPos.x, city.worldPos.y + 40f))
            }
            if (city.wasAttackingThisFrame) {
                SoundPlayer.play(city.getAttackSound())
                city.lastAttackTargetWorldPos?.let { target ->
                    mapHolder.unitLayer.addActor(AttackLineActor(city.worldPos.x, city.worldPos.y, target.x, target.y))
                }
            }
        }

        for ((unit, actor) in unitActors) {
            actor.centerOn(unit.worldPos.x, unit.worldPos.y)
            actor.updateFromUnit(cappedDelta)
            // Keep currentTile in sync with world position so terrain modifiers apply correctly
            context.tiles.minByOrNull { TacticalBattleContext.tileToWorldPos(it).dst(unit.worldPos) }
                ?.let { unit.currentTile = it }
        }
        for ((city, actor) in cityActors) {
            actor.updateFromCity(cappedDelta)
        }
        for ((unit, label) in healthLabels) {
            val state = when (unit.state) {
                TacticalUnitState.DEAD -> "dead".tr()
                TacticalUnitState.ESCAPED -> "fled".tr()
                else -> "${unit.currentHealth}${"hp".tr()}"
            }
            label.setText("${unit.getName().tr()}  $state")
        }
        for ((city, label) in cityHealthLabels) {
            val state = if (city.state == TacticalCityState.CAPTURED) "captured".tr() else "${city.currentHealth}${"hp".tr()}"
            label.setText("${city.getName().tr()}  $state")
        }

        // Start delay on first frame all enemies/player units are wiped out
        if (victoryDelayTimer < 0f && !battleOver) {
            when {
                result.playerWon -> victoryDelayTimer = VICTORY_DELAY
                result.enemyWon  -> victoryDelayTimer = VICTORY_DELAY
            }
        }
        // Count down with real delta (unaffected by speed multiplier) so the pause feels consistent
        if (victoryDelayTimer >= 0f) {
            victoryDelayTimer -= delta
            if (victoryDelayTimer <= 0f) {
                when {
                    result.playerWon -> { resultLabel.setText("Victory!".tr()); resultLabel.color = Color.GOLD }
                    result.enemyWon  -> { resultLabel.setText("Defeat!".tr());  resultLabel.color = Color.RED  }
                }
                battleOver = true
            }
        }
    }

    override fun show() {
        super.show()
        Gdx.graphics.isContinuousRendering = true
        Gdx.app.postRunnable {
            stage.act(0f)  // force table layout so mapHolder gets its actual width/height
            val mapContent = mapHolder.actor ?: return@postRunnable
            if (mapHolder.width <= 0f || mapHolder.height <= 0f) return@postRunnable

            val zoomX = mapHolder.width * 2.5f / mapContent.width
            val zoomY = mapHolder.height * 2.5f / mapContent.height
            val targetZoom = minOf(zoomX, zoomY).coerceIn(mapHolder.minZoom, mapHolder.maxZoom)
            mapHolder.zoom(targetZoom)

            // Center on the battlefield — scrollPercent 0.5 works for both cases:
            // when content fits inside viewport (padding handles it) and when larger.
            mapHolder.scrollPercentX = 0.5f
            mapHolder.scrollPercentY = 0.5f
            mapHolder.updateVisualScroll()

            // Scale unit positions outward from battle center so sides start far apart.
            // Preserves relative positions (flankers stay on flanks) while guaranteeing
            // meaningful travel time before contact.
            val battleCenter = mapHolder.getWorldPos(context.centerTile) ?: return@postRunnable
            battleCenterWorldPos = battleCenter
            val maxDeployPx = (context.radius - 0.5f) * TacticalUnit.HEX_SIZE * TacticalUnit.SQRT3
            for (unit in context.allUnits) {
                val tilePos = mapHolder.getWorldPos(unit.currentTile) ?: continue
                val offset = tilePos.cpy().sub(battleCenter).scl(DEPLOY_SCALE)
                if (offset.len() > maxDeployPx) offset.setLength(maxDeployPx)
                unit.worldPos.set(battleCenter.x + offset.x, battleCenter.y + offset.y)
            }
            for ((unit, actor) in unitActors) actor.centerOn(unit.worldPos.x, unit.worldPos.y)
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
        // Keep slider in sync with pinch/scroll zoom
        zoomSlider?.let { s ->
            val currentZoom = mapHolder.scaleX
            if (kotlin.math.abs(s.value - currentZoom) > 0.05f) {
                updatingSlider = true
                s.value = currentZoom
                updatingSlider = false
            }
        }
        stage.act()
        stage.draw()
    }

    private fun dismiss() {
        // Resolve each survivor's nearest tile using mapHolder coordinates (same space as worldPos)
        val usedTiles = mutableSetOf<com.unciv.logic.map.tile.Tile>()
        for (unit in context.allUnits.filter { it.state != com.unciv.logic.battle.tactical.TacticalUnitState.DEAD }) {
            val nearest = context.tiles
                .filter { tile ->
                    val pos = mapHolder.getWorldPos(tile)
                    pos != null && tile !in usedTiles && unit.sourceUnit.movement.canMoveTo(tile)
                }
                .minByOrNull { tile -> mapHolder.getWorldPos(tile)!!.dst(unit.worldPos) }
            if (nearest != null) {
                unit.finalTile = nearest
                usedTiles.add(nearest)
            }
        }
        result.applyToGame()
        game.popScreen()
        onDismiss?.invoke()
    }
}
