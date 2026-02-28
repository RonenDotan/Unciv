package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.UncivSound
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.GifAnimationActor
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.min

/**
 * Fullscreen overlay that displays an animated GIF centered on a dark background.
 * Dismissable by click anywhere, Space, Enter, or Escape.
 */
class EventVideoScreen(videoFile: FileHandle) : BaseScreen() {

    private val gifActor = GifAnimationActor(videoFile)
    private val bgColor = Color(0f, 0f, 0f, 1f)

    init {
        // Root table fills the stage
        val root = Table()
        root.setFillParent(true)
        stage.addActor(root)

        // Scale GIF to fit 90% of screen while preserving aspect ratio
        val maxW = stage.width * 0.9f
        val maxH = stage.height * 0.85f // Leave room for label
        val scale = min(maxW / gifActor.gifWidth, maxH / gifActor.gifHeight)
        val displayW = gifActor.gifWidth * scale
        val displayH = gifActor.gifHeight * scale

        root.add(gifActor).size(displayW, displayH).expand().center().row()

        // "Click to continue" label at bottom
        val labelStyle = Label.LabelStyle(skin.get(Label.LabelStyle::class.java))
        labelStyle.fontColor = Color(1f, 1f, 1f, 0.7f)
        val label = Label("Click to continue", labelStyle)
        label.setAlignment(Align.center)
        root.add(label).padBottom(20f).center()

        // Click anywhere to dismiss
        root.touchable = Touchable.enabled
        root.onClick(UncivSound.Silent) { dismiss() }

        // Key shortcuts to dismiss
        root.keyShortcuts.add(Input.Keys.SPACE) { dismiss() }
        root.keyShortcuts.add(Input.Keys.ENTER) { dismiss() }
        root.keyShortcuts.add(Input.Keys.ESCAPE) { dismiss() }
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
        Gdx.gl.glClearColor(bgColor.r, bgColor.g, bgColor.b, bgColor.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act()
        stage.draw()
    }

    private fun dismiss() {
        game.popScreen()
    }

    override fun dispose() {
        gifActor.dispose()
        super.dispose()
    }
}
