package com.unciv.ui.screens.tacticalbattle

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.logic.battle.tactical.TacticalUnit
import com.unciv.logic.battle.tactical.TacticalUnitState
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.images.ImageGetter
import com.unciv.models.UncivSound

/**
 * Scene2D actor representing a [TacticalUnit] on the battle map.
 * Uses the pixel unit sprite when available, falls back to a small icon.
 * Call [updateFromUnit] every frame to sync health bar and death state.
 */
class TacticalUnitActor(
    val tacticalUnit: TacticalUnit,
    tileSetStrings: TileSetStrings,
    onClicked: (TacticalUnitActor) -> Unit
) : Group() {

    private val healthBarBg: Image
    private val healthBarFg: Image
    private val selectionRing: Image

    private val spriteSize = TileGroupMap.groupSize * 1.5f  // 75f
    private val barHeight = 4f
    private val barWidth = spriteSize * 0.9f

    var isSelected = false
        set(value) {
            field = value
            selectionRing.isVisible = value
        }

    init {
        touchable = Touchable.enabled
        setSize(spriteSize, spriteSize + barHeight + 2f)

        // --- Selection ring (yellow, behind sprite) ---
        selectionRing = ImageGetter.getDot(Color.YELLOW).apply {
            setSize(spriteSize + 6f, spriteSize + 6f)
            setPosition(-3f, barHeight - 1f)
            isVisible = false
            color.a = 0.7f
        }
        addActor(selectionRing)

        // --- Pixel unit sprite or icon fallback ---
        val nation = tacticalUnit.sourceUnit.civ.nation
        val imageLocation = tileSetStrings.getUnitImageLocation(tacticalUnit.sourceUnit)

        val sprite: Group = if (imageLocation.isNotEmpty() && ImageGetter.imageExists(imageLocation)) {
            val layers = ImageGetter.getLayeredImageColored(
                imageLocation, null,
                nation.getInnerColor(),
                nation.getOuterColor()
            )
            Group().also { g ->
                g.setSize(spriteSize, spriteSize)
                for (layer in layers) { layer.setSize(spriteSize, spriteSize); g.addActor(layer) }
            }
        } else {
            val icon = ImageGetter.getUnitIcon(tacticalUnit.sourceUnit.baseUnit, nation.getInnerColor())
            icon.setSize(spriteSize * 0.6f, spriteSize * 0.6f)
            Group().also { g ->
                g.setSize(spriteSize, spriteSize)
                icon.setPosition((spriteSize - icon.width) / 2f, (spriteSize - icon.height) / 2f)
                g.addActor(icon)
            }
        }
        sprite.setPosition(0f, barHeight + 2f)
        addActor(sprite)

        // --- Health bar ---
        healthBarBg = ImageGetter.getDot(Color.DARK_GRAY).apply {
            setSize(barWidth, barHeight)
            setPosition((spriteSize - barWidth) / 2f, 0f)
        }
        healthBarFg = ImageGetter.getDot(Color.GREEN).apply {
            setSize(barWidth, barHeight)
            setPosition((spriteSize - barWidth) / 2f, 0f)
        }
        addActor(healthBarBg)
        addActor(healthBarFg)

        // --- Click handler ---
        addListener(object : InputListener() {
            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                onClicked(this@TacticalUnitActor)
                event.stop()  // prevent click reaching unitLayer background
                return true
            }
        })
    }

    /** Sync health bar and opacity with current unit state. */
    fun updateFromUnit() {
        val healthFraction = (tacticalUnit.currentHealth / 100f).coerceIn(0f, 1f)
        healthBarFg.setSize(barWidth * healthFraction, barHeight)
        healthBarFg.color = when {
            healthFraction > 0.6f -> Color.GREEN
            healthFraction > 0.3f -> Color.ORANGE
            else -> Color.RED
        }
        color.a = if (tacticalUnit.state == TacticalUnitState.DEAD) 0.25f else 1f
        selectionRing.isVisible = isSelected
    }

    /** Centers this actor on [worldX], [worldY] in TileGroupMap coordinates. */
    fun centerOn(worldX: Float, worldY: Float) {
        setPosition(worldX - width / 2f, worldY - height / 2f)
    }
}
