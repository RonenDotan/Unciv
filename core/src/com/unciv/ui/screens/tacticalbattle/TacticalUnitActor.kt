package com.unciv.ui.screens.tacticalbattle

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
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

private const val HIT_FLASH_DURATION = 0.3f

/**
 * Draws a clock-style arc above the unit showing remaining attack cooldown.
 * Dark circle background, blue arc sweeps clockwise from full → empty as the cooldown counts down.
 */
internal class CooldownIndicator(private val clockSize: Float) : Actor() {
    /** 1.0 = just attacked (full wait), 0.0 = ready to attack (hidden) */
    var fraction = 0f

    private val stagePos = Vector2()

    init { setSize(clockSize, clockSize) }

    override fun draw(batch: Batch, parentAlpha: Float) {
        if (fraction <= 0.01f) return
        localToStageCoordinates(stagePos.set(0f, 0f))
        val cx = stagePos.x + clockSize / 2f
        val cy = stagePos.y + clockSize / 2f
        val r  = clockSize / 2f

        batch.end()
        shapeRenderer.projectionMatrix = stage.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        // Dark background circle
        shapeRenderer.setColor(0f, 0f, 0f, 0.65f * parentAlpha)
        shapeRenderer.circle(cx, cy, r, 32)
        // Blue arc = remaining cooldown (sweeps clockwise from 12 o'clock)
        shapeRenderer.setColor(0.15f, 0.55f, 1f, 0.9f * parentAlpha)
        shapeRenderer.arc(cx, cy, r * 0.78f, 90f, -fraction * 360f, 32)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
        batch.begin()
    }

    companion object {
        val shapeRenderer: ShapeRenderer by lazy { ShapeRenderer() }
    }
}

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
    private val hitFlashOverlay: Image
    private val attackFlashOverlay: Image
    private val cooldownIndicator: CooldownIndicator
    private lateinit var sprite: Group

    private val spriteSize = TileGroupMap.groupSize * 1.5f  // 75f
    private val barHeight = 4f
    private val barWidth = spriteSize * 0.9f

    private var facingRight = !tacticalUnit.isPlayerControlled  // enemies face left initially

    var isSelected = false
        set(value) {
            field = value
            selectionRing.isVisible = value
        }

    private var hitFlashTimer = 0f
    private var attackFlashTimer = 0f
    private var displayAlpha = 1f

    init {
        touchable = Touchable.enabled
        setSize(spriteSize, spriteSize + barHeight + 2f)

        // --- Selection ring: small yellow circle centered on the sprite ---
        val ringSize = spriteSize * 0.55f
        selectionRing = ImageGetter.getCircle(Color.YELLOW).apply {
            setSize(ringSize, ringSize)
            setPosition(spriteSize / 2f - ringSize / 2f, (barHeight + 2f) + spriteSize / 2f - ringSize / 2f)
            isVisible = false
            color.a = 0.85f
        }
        addActor(selectionRing)

        // --- Pixel unit sprite or icon fallback ---
        val nation = tacticalUnit.sourceUnit.civ.nation
        val imageLocation = tileSetStrings.getUnitImageLocation(tacticalUnit.sourceUnit)

        sprite = if (imageLocation.isNotEmpty() && ImageGetter.imageExists(imageLocation)) {
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
        sprite.originX = spriteSize / 2f
        if (facingRight) sprite.scaleX = -1f  // sprites default to facing left; flip to face right
        addActor(sprite)

        // Red circle that flashes on hit
        hitFlashOverlay = ImageGetter.getCircle(Color.RED).apply {
            setSize(ringSize, ringSize)
            setPosition(spriteSize / 2f - ringSize / 2f, (barHeight + 2f) + spriteSize / 2f - ringSize / 2f)
            color.a = 0f
        }
        addActor(hitFlashOverlay)

        // Blue circle that flashes when this unit attacks
        attackFlashOverlay = ImageGetter.getCircle(Color.BLUE).apply {
            setSize(ringSize, ringSize)
            setPosition(spriteSize / 2f - ringSize / 2f, (barHeight + 2f) + spriteSize / 2f - ringSize / 2f)
            color.a = 0f
        }
        addActor(attackFlashOverlay)

        // Cooldown clock arc — shown above the sprite
        val clockSize = spriteSize * 0.45f
        cooldownIndicator = CooldownIndicator(clockSize).apply {
            setPosition(spriteSize / 2f - clockSize / 2f, barHeight + 2f + spriteSize + 4f)
        }
        addActor(cooldownIndicator)

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

    /** Sync health bar, hit flash, cooldown clock, opacity, and facing direction with current unit state. */
    fun updateFromUnit(delta: Float) {
        // Face toward commanded destination, commanded target, or current AI target
        val targetX = tacticalUnit.commandedDestination?.x
            ?: tacticalUnit.commandedTarget?.worldPos?.x
            ?: tacticalUnit.currentTarget?.worldPos?.x
        if (targetX != null) {
            val newFacingRight = targetX >= tacticalUnit.worldPos.x
            if (newFacingRight != facingRight) {
                facingRight = newFacingRight
                sprite.scaleX = if (facingRight) -1f else 1f  // sprites default left; -1 = facing right
            }
        }

        // Hit flash: red circle on the defender
        if (tacticalUnit.wasHitThisFrame) {
            tacticalUnit.wasHitThisFrame = false
            hitFlashTimer = HIT_FLASH_DURATION
        }
        hitFlashTimer = (hitFlashTimer - delta).coerceAtLeast(0f)
        hitFlashOverlay.color.a = (hitFlashTimer / HIT_FLASH_DURATION) * 0.7f

        // Attack flash: blue circle on the attacker
        if (tacticalUnit.wasAttackingThisFrame) {
            tacticalUnit.wasAttackingThisFrame = false
            attackFlashTimer = HIT_FLASH_DURATION
        }
        attackFlashTimer = (attackFlashTimer - delta).coerceAtLeast(0f)
        attackFlashOverlay.color.a = (attackFlashTimer / HIT_FLASH_DURATION) * 0.7f

        // Cooldown clock arc
        cooldownIndicator.fraction = (tacticalUnit.cooldownRemaining / tacticalUnit.attackCooldownSeconds)
            .coerceIn(0f, 1f)

        // Smooth fade to 25% alpha on death
        val targetAlpha = if (tacticalUnit.state == TacticalUnitState.DEAD || tacticalUnit.state == TacticalUnitState.ESCAPED) 0f else 1f
        displayAlpha += (targetAlpha - displayAlpha) * (delta * 4f)
        color.a = displayAlpha

        // Health bar
        val healthFraction = (tacticalUnit.currentHealth / 100f).coerceIn(0f, 1f)
        healthBarFg.setSize(barWidth * healthFraction, barHeight)
        healthBarFg.color = when {
            healthFraction > 0.6f -> Color.GREEN
            healthFraction > 0.3f -> Color.ORANGE
            else -> Color.RED
        }
    }

    /** Centers this actor on [worldX], [worldY] in TileGroupMap coordinates. */
    fun centerOn(worldX: Float, worldY: Float) {
        setPosition(worldX - width / 2f, worldY - height / 2f)
    }
}
