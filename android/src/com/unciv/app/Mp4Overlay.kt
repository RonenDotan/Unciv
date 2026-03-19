package com.unciv.app

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.VideoView
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle

/**
 * Shows an MP4 video as an Android View overlay on top of the LibGDX GLSurfaceView.
 * Handles its own UI: dark background, video, "Tap to continue" label, and tap-to-dismiss.
 */
class Mp4Overlay(
    private val activity: Activity,
    file: FileHandle,
    onFirstLoopComplete: () -> Unit,
    onUserDismiss: () -> Unit
) {
    private val container = FrameLayout(activity)
    private val videoView = VideoView(activity)
    private var completed = false
    private var canDismiss = false

    companion object {
        private const val DISMISS_AFTER_MS = 4000L
    }

    init {
        container.setBackgroundColor(Color.argb(180, 0, 0, 0))

        val fillParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
        }
        container.addView(videoView, fillParams)

        val label = TextView(activity).apply {
            text = "Tap to continue"
            setTextColor(Color.argb(204, 255, 255, 255))
            textSize = 18f
            visibility = View.INVISIBLE
        }
        val labelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 60
        }
        container.addView(label, labelParams)

        videoView.setVideoPath(file.file().absolutePath)

        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            mp.start()
        }

        videoView.setOnCompletionListener {
            if (!completed) {
                completed = true
                canDismiss = true
                label.visibility = View.VISIBLE
                // Loop the video after first play
                videoView.seekTo(0)
                videoView.start()
                Gdx.app.postRunnable { onFirstLoopComplete() }
            }
        }

        container.setOnClickListener {
            if (canDismiss) dismiss(onUserDismiss)
        }

        // Allow dismiss after 4 seconds even if video hasn't finished its first loop
        Handler(Looper.getMainLooper()).postDelayed({
            if (!completed) {
                completed = true
                canDismiss = true
                label.visibility = View.VISIBLE
                Gdx.app.postRunnable { onFirstLoopComplete() }
            }
        }, DISMISS_AFTER_MS)

        activity.runOnUiThread {
            val root = activity.window.decorView as ViewGroup
            root.addView(container, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
    }

    private fun dismiss(onUserDismiss: () -> Unit) {
        activity.runOnUiThread {
            videoView.stopPlayback()
            (activity.window.decorView as ViewGroup).removeView(container)
        }
        Gdx.app.postRunnable { onUserDismiss() }
    }
}
