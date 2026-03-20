package com.unciv.ui.screens.tacticalbattle

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter

/**
 * An expanding ring that bursts outward and fades when a unit dies.
 * Add to unitLayer centered on the dead unit's worldPos.
 */
class DeathEffectActor(centerX: Float, centerY: Float, size: Float) : Group() {
    init {
        val ring = ImageGetter.getCircle(Color.ORANGE).apply {
            setSize(size, size)
            setPosition(-size / 2f, -size / 2f)
        }
        addActor(ring)
        setPosition(centerX, centerY)
        setOrigin(0f, 0f)

        addAction(Actions.sequence(
            Actions.parallel(
                Actions.scaleBy(2f, 2f, 0.55f, Interpolation.pow2Out),
                Actions.sequence(
                    Actions.alpha(1f, 0.05f),   // pop in instantly
                    Actions.fadeOut(0.5f, Interpolation.pow2In)
                )
            ),
            Actions.removeActor()
        ))
    }
}

/**
 * A damage number that rises and fades above a hit unit.
 * Add to unitLayer at the unit's worldPos.
 */
class FloatingTextActor(damage: Int, startX: Float, startY: Float) : Group() {
    private var elapsed = 0f
    private val duration = 1.2f
    private val riseSpeed = 30f

    init {
        val label = "-$damage".toLabel(Color.RED, 16)
        addActor(label)
        setSize(label.prefWidth, label.prefHeight)
        setPosition(startX - label.prefWidth / 2f, startY)
    }

    override fun act(delta: Float) {
        super.act(delta)
        elapsed += delta
        if (elapsed >= duration) { remove(); return }
        y += riseSpeed * delta
        val t = elapsed / duration
        color.a = 1f - t * t  // fast at first, then slows
    }
}

/**
 * A brief line drawn from attacker to defender when an attack lands.
 * Add to unitLayer; removes itself after fading out.
 */
class AttackLineActor(
    private val fromX: Float, private val fromY: Float,
    private val toX: Float,   private val toY: Float
) : Actor() {
    private var elapsed = 0f
    private val duration = 0.4f
    private val stageFrom = Vector2()
    private val stageTo   = Vector2()

    override fun act(delta: Float) {
        elapsed += delta
        if (elapsed >= duration) remove()
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        if (elapsed >= duration) return
        val alpha = (1f - elapsed / duration) * parentAlpha

        localToStageCoordinates(stageFrom.set(fromX, fromY))
        localToStageCoordinates(stageTo.set(toX, toY))

        batch.end()
        shapeRenderer.projectionMatrix = stage.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(1f, 0.85f, 0.1f, alpha)
        shapeRenderer.line(stageFrom, stageTo)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
        batch.begin()
    }

    companion object {
        val shapeRenderer: ShapeRenderer by lazy { ShapeRenderer() }
    }
}
