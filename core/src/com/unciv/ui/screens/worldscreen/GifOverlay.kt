package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.UncivSound
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.GifAnimationActor
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.VideoPlayerFactory
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.min

/**
 * A stage overlay that plays a GIF or MP4 animation on top of the WorldScreen.
 *
 * - GIF: rendered directly in the LibGDX scene via [GifAnimationActor].
 * - MP4: delegated to [VideoPlayerFactory.playMp4] (Android-only; no-op on desktop).
 *
 * Dismissed by click or keyboard (Space/Enter/Escape) after the video finishes once.
 */
class GifOverlay(
    stage: Stage,
    videoFile: FileHandle,
    private val onDismiss: () -> Unit
) : Group() {

    private val gifActor: GifAnimationActor?
    private var canDismiss = false

    init {
        setSize(stage.width, stage.height)
        touchable = Touchable.enabled

        val bg = Image(ImageGetter.getWhiteDotDrawable())
        bg.color = Color(0f, 0f, 0f, 0.6f)
        bg.setSize(stage.width, stage.height)
        addActor(bg)

        val labelStyle = Label.LabelStyle(BaseScreen.skin.get(Label.LabelStyle::class.java))
        labelStyle.fontColor = Color(1f, 1f, 1f, 0.8f)
        val label = Label("Click to continue", labelStyle)
        label.setAlignment(Align.center)
        label.pack()
        label.isVisible = false
        addActor(label)

        val onVideoComplete = {
            canDismiss = true
            label.isVisible = true
        }

        if (videoFile.extension() == "mp4") {
            gifActor = null
            val factory = VideoPlayerFactory.playMp4
            if (factory != null) {
                // MP4: the Android overlay handles its own video UI and tap-to-dismiss.
                // This Group stays as a transparent placeholder that cleans up on completion.
                factory(videoFile, onVideoComplete) { dismiss() }
                // Label position below center (will show after Android overlay is removed)
                label.setPosition((stage.width - label.width) / 2f, (stage.height - label.height) / 2f - 40f)
            } else {
                // Desktop or no factory registered — no MP4 support, allow immediate skip
                canDismiss = true
                label.isVisible = true
                label.setPosition((stage.width - label.width) / 2f, (stage.height - label.height) / 2f)
            }
        } else {
            // GIF path
            val gif = GifAnimationActor(videoFile)
            gifActor = gif

            val maxW = stage.width * 0.75f
            val maxH = stage.height * 0.75f
            val scale = min(maxW / gif.gifWidth, maxH / gif.gifHeight)
            val displayW = gif.gifWidth * scale
            val displayH = gif.gifHeight * scale
            val gifY = (stage.height - displayH) / 2f + 15f

            gif.setSize(displayW, displayH)
            gif.setPosition((stage.width - displayW) / 2f, gifY)
            addActor(gif)

            label.setPosition((stage.width - label.width) / 2f, gifY - label.height - 8f)

            gif.onComplete = onVideoComplete

            Gdx.graphics.isContinuousRendering = true
        }

        onClick(UncivSound.Silent) { if (canDismiss) dismiss() }
        keyShortcuts.add(Input.Keys.SPACE) { if (canDismiss) dismiss() }
        keyShortcuts.add(Input.Keys.ENTER) { if (canDismiss) dismiss() }
        keyShortcuts.add(Input.Keys.ESCAPE) { if (canDismiss) dismiss() }
    }

    private fun dismiss() {
        Gdx.graphics.isContinuousRendering = UncivGame.Current.settings.continuousRendering
        gifActor?.dispose()
        remove()
        onDismiss()
    }
}
