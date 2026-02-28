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
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.min

/**
 * A stage overlay that plays a GIF animation on top of the WorldScreen.
 * The world map remains visible around/behind the GIF.
 * Dismissed by click or keyboard (Space/Enter/Escape).
 */
class GifOverlay(
    stage: Stage,
    videoFile: FileHandle,
    private val onDismiss: () -> Unit
) : Group() {

    private val gifActor = GifAnimationActor(videoFile)

    init {
        setSize(stage.width, stage.height)
        touchable = Touchable.enabled

        // Semi-transparent dark background so the world shows through
        val bg = Image(ImageGetter.getWhiteDotDrawable())
        bg.color = Color(0f, 0f, 0f, 0.6f)
        bg.setSize(stage.width, stage.height)
        addActor(bg)

        // Scale GIF to fit 75% of stage, preserving aspect ratio
        val maxW = stage.width * 0.75f
        val maxH = stage.height * 0.75f
        val scale = min(maxW / gifActor.gifWidth, maxH / gifActor.gifHeight)
        val displayW = gifActor.gifWidth * scale
        val displayH = gifActor.gifHeight * scale

        val gifY = (stage.height - displayH) / 2f + 15f  // nudge up slightly to leave room for label
        gifActor.setSize(displayW, displayH)
        gifActor.setPosition((stage.width - displayW) / 2f, gifY)
        addActor(gifActor)

        // "Click to continue" label below the GIF
        val labelStyle = Label.LabelStyle(BaseScreen.skin.get(Label.LabelStyle::class.java))
        labelStyle.fontColor = Color(1f, 1f, 1f, 0.8f)
        val label = Label("Click to continue", labelStyle)
        label.setAlignment(Align.center)
        label.pack()
        label.setPosition((stage.width - label.width) / 2f, gifY - label.height - 8f)
        addActor(label)

        onClick(UncivSound.Silent) { dismiss() }
        keyShortcuts.add(Input.Keys.SPACE) { dismiss() }
        keyShortcuts.add(Input.Keys.ENTER) { dismiss() }
        keyShortcuts.add(Input.Keys.ESCAPE) { dismiss() }

        // GIF needs the render loop to fire continuously, not just on input events
        Gdx.graphics.isContinuousRendering = true
    }

    private fun dismiss() {
        Gdx.graphics.isContinuousRendering = UncivGame.Current.settings.continuousRendering
        gifActor.dispose()
        remove()
        onDismiss()
    }
}
