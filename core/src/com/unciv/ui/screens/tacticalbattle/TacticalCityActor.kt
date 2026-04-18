package com.unciv.ui.screens.tacticalbattle

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.logic.battle.tactical.TacticalCity
import com.unciv.logic.battle.tactical.TacticalCityState
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.images.ImageGetter

private const val CITY_HIT_FLASH_DURATION = 0.3f

/**
 * Scene2D actor representing a [TacticalCity] on the battle map.
 * Shows the city icon with a health bar and red hit flash.
 */
class TacticalCityActor(val tacticalCity: TacticalCity) : Group() {

    private val spriteSize = TileGroupMap.groupSize * 1.5f
    private val barHeight = 4f
    private val barWidth = spriteSize * 0.9f

    private val healthBarBg: Image
    private val healthBarFg: Image
    private val hitFlashOverlay: Image
    private val capturedOverlay: Image

    private var hitFlashTimer = 0f

    init {
        setSize(spriteSize, spriteSize + barHeight + 2f)

        // City icon centered in sprite area
        val icon = ImageGetter.getImage("OtherIcons/Cities")
        icon.setSize(spriteSize * 0.7f, spriteSize * 0.7f)
        icon.setPosition((spriteSize - icon.width) / 2f, barHeight + 2f + (spriteSize - icon.height) / 2f)
        icon.color = Color.WHITE
        addActor(icon)

        // Red hit flash circle
        val ringSize = spriteSize * 0.55f
        hitFlashOverlay = ImageGetter.getCircle(Color.RED).apply {
            setSize(ringSize, ringSize)
            setPosition(spriteSize / 2f - ringSize / 2f, barHeight + 2f + spriteSize / 2f - ringSize / 2f)
            color.a = 0f
        }
        addActor(hitFlashOverlay)

        // Gray overlay when captured
        capturedOverlay = ImageGetter.getDot(Color.DARK_GRAY).apply {
            setSize(spriteSize, spriteSize)
            setPosition(0f, barHeight + 2f)
            color.a = 0f
        }
        addActor(capturedOverlay)

        // Health bar
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

    fun updateFromCity(delta: Float) {
        if (tacticalCity.wasHitThisFrame) {
            tacticalCity.wasHitThisFrame = false
            hitFlashTimer = CITY_HIT_FLASH_DURATION
        }
        hitFlashTimer = (hitFlashTimer - delta).coerceAtLeast(0f)
        hitFlashOverlay.color.a = (hitFlashTimer / CITY_HIT_FLASH_DURATION) * 0.7f

        val healthFraction = (tacticalCity.currentHealth.toFloat() / tacticalCity.maxHealth).coerceIn(0f, 1f)
        healthBarFg.setSize(barWidth * healthFraction, barHeight)
        healthBarFg.color = when {
            healthFraction > 0.6f -> Color.GREEN
            healthFraction > 0.3f -> Color.ORANGE
            else -> Color.RED
        }

        capturedOverlay.color.a = if (tacticalCity.state == TacticalCityState.CAPTURED) 0.6f else 0f
    }

    fun centerOn(worldX: Float, worldY: Float) {
        setPosition(worldX - width / 2f, worldY - height / 2f)
    }
}
