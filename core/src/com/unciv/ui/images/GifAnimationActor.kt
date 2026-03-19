package com.unciv.ui.images

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.utils.Disposable

/**
 * Scene2D [Widget] that plays a decoded GIF as an animation.
 * Creates one [Texture] at a time from decoded [Pixmap], disposes previous on frame advance.
 */
class GifAnimationActor(file: FileHandle) : Widget(), Disposable {

    private val decoder = GifDecoder(file)
    private var currentFrame = 0
    private var elapsed = 0f
    private var currentTexture: Texture? = null
    private var playing = true
    private var hasCompleted = false

    /** Called once when the GIF finishes its first full playthrough. */
    var onComplete: (() -> Unit)? = null

    val isFinished: Boolean
        get() = currentFrame >= decoder.frameCount - 1 &&
            elapsed >= decoder.frames.last().delay

    val gifWidth get() = decoder.width
    val gifHeight get() = decoder.height

    init {
        showFrame(0)
    }

    fun play() { playing = true }
    fun pause() { playing = false }
    fun stop() {
        playing = false
        currentFrame = 0
        elapsed = 0f
        showFrame(0)
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (!playing || decoder.frameCount <= 1) return

        elapsed += delta
        val frameDelay = decoder.frames[currentFrame].delay
        if (elapsed >= frameDelay) {
            elapsed -= frameDelay
            currentFrame++
            if (currentFrame >= decoder.frameCount) {
                currentFrame = 0 // Loop
                if (!hasCompleted) {
                    hasCompleted = true
                    onComplete?.invoke()
                }
            }
            showFrame(currentFrame)
        }
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val tex = currentTexture ?: return
        val c = color
        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha)
        batch.draw(tex, x, y, width, height)
    }

    override fun getPrefWidth() = decoder.width.toFloat()
    override fun getPrefHeight() = decoder.height.toFloat()

    private fun showFrame(index: Int) {
        val pixmap = decoder.decodeFrame(index)
        val oldTex = currentTexture
        currentTexture = Texture(pixmap)
        // Don't dispose pixmap — it's the decoder's canvas, owned by decoder
        oldTex?.dispose()
    }

    override fun dispose() {
        currentTexture?.dispose()
        currentTexture = null
        decoder.dispose()
    }
}
