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
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Full-screen tactical battle overlay.
 * Phase 1 (minimal): shows battle info and a dismiss button.
 * Later phases will add real-time unit simulation and hex map rendering.
 */
class TacticalBattleScreen(private val context: TacticalBattleContext) : BaseScreen() {

    private val result = TacticalBattleResult(context.playerUnits, context.enemyUnits)

    init {
        val root = Table()
        root.setFillParent(true)
        root.background = skinStrings.getUiBackground("General/Border", tintColor = Color(0f, 0f, 0.1f, 0.95f))
        stage.addActor(root)

        root.add("⚔ Tactical Battle".toLabel(Color.GOLD, 36)).padBottom(20f).row()

        root.add(buildInfoTable()).padBottom(30f).row()

        val dismissButton = "End Battle".toLabel(Color.WHITE, 20)
            .apply { setAlignment(Align.center) }
        val buttonCell = root.add(dismissButton)
        buttonCell.width(200f).height(50f).padBottom(20f)

        dismissButton.onClick { dismiss() }
        root.keyShortcuts.add(Input.Keys.ESCAPE) { dismiss() }
        root.keyShortcuts.add(Input.Keys.ENTER) { dismiss() }
        root.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
    }

    private fun buildInfoTable(): Table {
        val table = Table()
        val labelStyle = Label.LabelStyle(skin.get(Label.LabelStyle::class.java))

        table.add("Your units: ${context.playerUnits.size}".toLabel(Color.GREEN, 20)).padBottom(8f).row()
        table.add("Enemy units: ${context.enemyUnits.size}".toLabel(Color.RED, 20)).padBottom(8f).row()
        table.add("Battle radius: ${context.radius} tiles".toLabel(Color.WHITE, 16)).padBottom(4f).row()

        for (unit in context.playerUnits)
            table.add("  ✦ ${unit.getName()} (HP ${unit.health})".toLabel(Color.GREEN, 14)).left().row()
        for (unit in context.enemyUnits)
            table.add("  ✦ ${unit.getName()} (HP ${unit.health})".toLabel(Color.RED, 14)).left().row()

        return table
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
        stage.act()
        stage.draw()
    }

    private fun dismiss() {
        result.applyToGame()
        game.popScreen()
    }
}
