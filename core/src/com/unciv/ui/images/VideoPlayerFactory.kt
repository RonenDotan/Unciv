package com.unciv.ui.images

import com.badlogic.gdx.files.FileHandle

/**
 * Platform-injectable factory for MP4 video playback.
 * Set by the Android launcher at startup; null on desktop (no MP4 support).
 *
 * [playMp4] receives:
 *  - [file]                 the MP4 FileHandle to play
 *  - [onFirstLoopComplete]  called (on GL thread) after the video finishes its first loop
 *  - [onUserDismiss]        called (on GL thread) when the user taps to dismiss
 */
object VideoPlayerFactory {
    var playMp4: ((file: FileHandle, onFirstLoopComplete: () -> Unit, onUserDismiss: () -> Unit) -> Unit)? = null
}
