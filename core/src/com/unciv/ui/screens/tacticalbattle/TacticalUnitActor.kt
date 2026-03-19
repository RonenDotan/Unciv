package com.unciv.ui.screens.tacticalbattle

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.logic.battle.tactical.TacticalUnit
import com.unciv.logic.battle.tactical.TacticalUnitState
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.images.ImageGetter

/**
 * Scene2D actor representing a [TacticalUnit] on the battle map.
 * Uses the pixel unit sprite when available, falls back to a small icon.
 * Call [updateFromUnit] every frame to sync health bar and death state.
 */
class TacticalUnitActor(val tacticalUnit: TacticalUnit, tileSetStrings: TileSetStrings) : Group() {

    private val healthBarBg: Image
    private val healthBarFg: Image

    // Sprite fills the tile; health bar is a thin strip above it
    private val spriteSize = TileGroupMap.groupSize  // 50f — same as a tile
    private val barHeight = 4f
    private val barWidth = spriteSize * 0.9f

    init {
        setSize(spriteSize, spriteSize + barHeight + 2f)

        // --- Pixel unit sprite (or small icon fallback) ---
        val nation = tacticalUnit.sourceUnit.civ.nation
        val imageLocation = tileSetStrings.getUnitImageLocation(tacticalUnit.sourceUnit)

        val sprite: Group = if (imageLocation.isNotEmpty() && ImageGetter.imageExists(imageLocation)) {
            // Pixel sprite — layered and civ-colored, same as world map
            val layers = ImageGetter.getLayeredImageColored(
                imageLocation, null,
                nation.getInnerColor(),
                nation.getOuterColor()
            )
            Group().also { g ->
                g.setSize(spriteSize, spriteSize)
                for (layer in layers) {
                    layer.setSize(spriteSize, spriteSize)
                    g.addActor(layer)
                }
            }
        } else {
            // Fallback: small civ-colored unit icon
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
    }

    /** Sync health bar width/color and opacity with current unit state. */
    fun updateFromUnit() {
        val healthFraction = (tacticalUnit.currentHealth / 100f).coerceIn(0f, 1f)
        healthBarFg.setSize(barWidth * healthFraction, barHeight)
        healthBarFg.color = when {
            healthFraction > 0.6f -> Color.GREEN
            healthFraction > 0.3f -> Color.ORANGE
            else -> Color.RED
        }
        color.a = if (tacticalUnit.state == TacticalUnitState.DEAD) 0.25f else 1f
    }

    /** Centers this actor on [worldX], [worldY] in TileGroupMap coordinates. */
    fun centerOn(worldX: Float, worldY: Float) {
        setPosition(worldX - width / 2f, worldY - height / 2f)
    }
}
