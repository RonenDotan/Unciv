package com.unciv.ui.images

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import java.io.BufferedInputStream
import java.io.InputStream

/**
 * Pure-Java GIF89a decoder — no AWT/ImageIO, Android-compatible.
 * Reads GIF binary format directly from a [FileHandle].
 *
 * Only 1-2 pixmaps are alive at a time (canvas + previous for disposal method 3).
 */
class GifDecoder(file: FileHandle) {

    class GifFrame(
        val x: Int, val y: Int, val width: Int, val height: Int,
        val delay: Float, // seconds
        val disposalMethod: Int,
        val transparentColorIndex: Int, // -1 if none
        val interlaced: Boolean,
        val localColorTable: IntArray?, // null → use global
        val lzwData: ByteArray,
        val lzwMinCodeSize: Int
    )

    val width: Int
    val height: Int
    val frames: List<GifFrame>

    private val globalColorTable: IntArray?
    private var canvas: Pixmap? = null
    private var previousCanvas: Pixmap? = null
    private var lastDecodedFrame = -1

    init {
        val stream = BufferedInputStream(file.read())
        stream.use { s ->
            // Header
            val header = ByteArray(6)
            s.readFully(header)
            val sig = String(header, Charsets.US_ASCII)
            require(sig == "GIF87a" || sig == "GIF89a") { "Not a GIF file" }

            // Logical Screen Descriptor
            width = s.readUShort()
            height = s.readUShort()
            val packed = s.readUByte()
            val hasGlobalCT = (packed and 0x80) != 0
            val globalCTSize = 1 shl ((packed and 0x07) + 1)
            s.readUByte() // background color index
            s.readUByte() // pixel aspect ratio

            globalColorTable = if (hasGlobalCT) readColorTable(s, globalCTSize) else null

            val parsedFrames = mutableListOf<GifFrame>()
            var delay = 0.1f
            var disposalMethod = 0
            var transparentIndex = -1

            // Parse blocks
            loop@ while (true) {
                when (val blockType = s.read()) {
                    0x2C -> { // Image Descriptor
                        val ix = s.readUShort()
                        val iy = s.readUShort()
                        val iw = s.readUShort()
                        val ih = s.readUShort()
                        val imgPacked = s.readUByte()
                        val hasLocalCT = (imgPacked and 0x80) != 0
                        val interlaced = (imgPacked and 0x40) != 0
                        val localCTSize = 1 shl ((imgPacked and 0x07) + 1)
                        val localCT = if (hasLocalCT) readColorTable(s, localCTSize) else null

                        val lzwMinCode = s.readUByte()
                        val lzwData = readSubBlocks(s)

                        parsedFrames.add(GifFrame(
                            ix, iy, iw, ih, delay, disposalMethod,
                            transparentIndex, interlaced, localCT, lzwData, lzwMinCode
                        ))

                        // Reset per-frame GCE values
                        delay = 0.1f
                        disposalMethod = 0
                        transparentIndex = -1
                    }
                    0x21 -> { // Extension
                        when (s.readUByte()) {
                            0xF9 -> { // Graphic Control Extension
                                s.readUByte() // block size (always 4)
                                val gcePacked = s.readUByte()
                                disposalMethod = (gcePacked shr 2) and 0x07
                                val hasTransparent = (gcePacked and 0x01) != 0
                                val delayCs = s.readUShort()
                                delay = if (delayCs == 0) 0.1f else delayCs / 100f
                                val transIdx = s.readUByte()
                                transparentIndex = if (hasTransparent) transIdx else -1
                                s.readUByte() // block terminator
                            }
                            else -> skipSubBlocks(s) // Comment, Application, etc.
                        }
                    }
                    0x3B, -1 -> break@loop // Trailer or EOF
                    else -> break@loop
                }
            }
            frames = parsedFrames
        }
    }

    /** Number of frames in the GIF. */
    val frameCount get() = frames.size

    /** Total duration in seconds for one loop. */
    val totalDuration get() = frames.sumOf { it.delay.toDouble() }.toFloat()

    /**
     * Decode frame at [index], compositing onto the canvas using proper disposal.
     * Returns the canvas pixmap — caller must NOT dispose it (owned by decoder).
     */
    fun decodeFrame(index: Int): Pixmap {
        require(index in frames.indices) { "Frame index out of range: $index" }

        if (canvas == null) {
            canvas = Pixmap(width, height, Pixmap.Format.RGBA8888)
            canvas!!.setColor(Color.CLEAR)
            canvas!!.fill()
        }

        // Reset if going back to start
        if (index == 0) {
            canvas!!.setColor(Color.CLEAR)
            canvas!!.fill()
            previousCanvas?.dispose()
            previousCanvas = null
            lastDecodedFrame = -1
        }

        // Apply disposal of previous frame before drawing the new one
        if (lastDecodedFrame >= 0 && lastDecodedFrame < frames.size) {
            val prev = frames[lastDecodedFrame]
            val canvasPix = canvas!!
            canvasPix.blending = Pixmap.Blending.None
            when (prev.disposalMethod) {
                2 -> {
                    // Restore to background (clear the previous frame's area)
                    canvasPix.setColor(Color.CLEAR)
                    canvasPix.fillRectangle(prev.x, prev.y, prev.width, prev.height)
                }
                3 -> {
                    // Restore to previous saved state
                    if (previousCanvas != null) {
                        canvasPix.drawPixmap(previousCanvas, 0, 0)
                    }
                }
                // 0, 1: do not dispose — leave canvas as-is
            }
            canvasPix.blending = Pixmap.Blending.SourceOver
        }

        // Decode this frame
        val frame = frames[index]
        val colorTable = frame.localColorTable ?: globalColorTable
            ?: throw IllegalStateException("No color table available")

        // Save canvas before drawing if this frame's disposal is "restore to previous"
        if (frame.disposalMethod == 3) {
            if (previousCanvas == null)
                previousCanvas = Pixmap(width, height, Pixmap.Format.RGBA8888)
            previousCanvas!!.blending = Pixmap.Blending.None
            previousCanvas!!.drawPixmap(canvas, 0, 0)
            previousCanvas!!.blending = Pixmap.Blending.SourceOver
        }

        // LZW decode
        val pixels = decodeLZW(frame.lzwData, frame.lzwMinCodeSize, frame.width * frame.height)

        // Write pixels to canvas
        val canvasPix = canvas!!
        canvasPix.blending = Pixmap.Blending.None

        var pi = 0
        if (frame.interlaced) {
            val starts = intArrayOf(0, 4, 2, 1)
            val steps = intArrayOf(8, 8, 4, 2)
            for (pass in 0..3) {
                var y = starts[pass]
                while (y < frame.height) {
                    for (x in 0 until frame.width) {
                        if (pi < pixels.size) {
                            val colorIdx = pixels[pi++].toInt() and 0xFF
                            if (colorIdx != frame.transparentColorIndex) {
                                canvasPix.drawPixel(frame.x + x, frame.y + y, colorTable[colorIdx])
                            }
                        }
                    }
                    y += steps[pass]
                }
            }
        } else {
            for (y in 0 until frame.height) {
                for (x in 0 until frame.width) {
                    if (pi < pixels.size) {
                        val colorIdx = pixels[pi++].toInt() and 0xFF
                        if (colorIdx != frame.transparentColorIndex) {
                            canvasPix.drawPixel(frame.x + x, frame.y + y, colorTable[colorIdx])
                        }
                    }
                }
            }
        }

        canvasPix.blending = Pixmap.Blending.SourceOver
        lastDecodedFrame = index
        return canvasPix
    }

    fun dispose() {
        canvas?.dispose()
        canvas = null
        previousCanvas?.dispose()
        previousCanvas = null
    }

    // ──────────── LZW Decoder ────────────

    private fun decodeLZW(data: ByteArray, minCodeSize: Int, pixelCount: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        val output = ByteArray(pixelCount)
        var outPos = 0

        // Table as parallel arrays for speed
        var tableSize = eoiCode + 1
        val maxTableSize = 4096
        val tablePrev = IntArray(maxTableSize)
        val tableChar = ByteArray(maxTableSize)
        val tableLen = IntArray(maxTableSize)

        fun initTable() {
            tableSize = eoiCode + 1
            for (i in 0 until clearCode) {
                tablePrev[i] = -1
                tableChar[i] = i.toByte()
                tableLen[i] = 1
            }
        }

        fun getString(code: Int, buf: ByteArray, offset: Int): Int {
            var c = code
            val len = tableLen[c]
            var pos = offset + len - 1
            while (c >= 0 && pos >= offset) {
                buf[pos--] = tableChar[c]
                c = tablePrev[c]
            }
            return len
        }

        initTable()
        var codeSize = minCodeSize + 1
        var codeMask = (1 shl codeSize) - 1

        var bitBuf = 0
        var bitCount = 0
        var dataPos = 0
        var prevCode = -1
        val tempBuf = ByteArray(maxTableSize)

        fun nextCode(): Int {
            while (bitCount < codeSize) {
                if (dataPos >= data.size) return eoiCode
                bitBuf = bitBuf or ((data[dataPos++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            val code = bitBuf and codeMask
            bitBuf = bitBuf ushr codeSize
            bitCount -= codeSize
            return code
        }

        while (outPos < pixelCount) {
            val code = nextCode()
            if (code == eoiCode) break
            if (code == clearCode) {
                initTable()
                codeSize = minCodeSize + 1
                codeMask = (1 shl codeSize) - 1
                prevCode = -1
                continue
            }

            if (prevCode == -1) {
                // First code after clear
                if (code < tableSize) {
                    val len = getString(code, tempBuf, 0)
                    val copyLen = minOf(len, pixelCount - outPos)
                    System.arraycopy(tempBuf, 0, output, outPos, copyLen)
                    outPos += copyLen
                }
                prevCode = code
                continue
            }

            val firstChar: Byte
            if (code < tableSize) {
                val len = getString(code, tempBuf, 0)
                val copyLen = minOf(len, pixelCount - outPos)
                System.arraycopy(tempBuf, 0, output, outPos, copyLen)
                outPos += copyLen
                firstChar = tempBuf[0]
            } else {
                // code == tableSize (the special KωK case)
                val len = getString(prevCode, tempBuf, 0)
                firstChar = tempBuf[0]
                tempBuf[len] = firstChar
                val copyLen = minOf(len + 1, pixelCount - outPos)
                System.arraycopy(tempBuf, 0, output, outPos, copyLen)
                outPos += copyLen
            }

            // Add to table
            if (tableSize < maxTableSize) {
                tablePrev[tableSize] = prevCode
                tableChar[tableSize] = firstChar
                tableLen[tableSize] = tableLen[prevCode] + 1
                tableSize++
                if (tableSize > codeMask && codeSize < 12) {
                    codeSize++
                    codeMask = (1 shl codeSize) - 1
                }
            }
            prevCode = code
        }

        return output
    }

    // ──────────── Binary reading helpers ────────────

    companion object {
        private fun InputStream.readUByte(): Int {
            val b = read()
            if (b < 0) throw IllegalStateException("Unexpected end of GIF stream")
            return b
        }

        private fun InputStream.readUShort(): Int {
            val lo = readUByte()
            val hi = readUByte()
            return lo or (hi shl 8)
        }

        private fun InputStream.readFully(buf: ByteArray) {
            var off = 0
            while (off < buf.size) {
                val n = read(buf, off, buf.size - off)
                if (n < 0) throw IllegalStateException("Unexpected end of GIF stream")
                off += n
            }
        }

        private fun readColorTable(stream: InputStream, size: Int): IntArray {
            val ct = IntArray(size)
            val buf = ByteArray(size * 3)
            stream.readFully(buf)
            for (i in 0 until size) {
                val r = buf[i * 3].toInt() and 0xFF
                val g = buf[i * 3 + 1].toInt() and 0xFF
                val b = buf[i * 3 + 2].toInt() and 0xFF
                // RGBA8888 format for Pixmap.drawPixel
                ct[i] = (r shl 24) or (g shl 16) or (b shl 8) or 0xFF
            }
            return ct
        }

        private fun readSubBlocks(stream: InputStream): ByteArray {
            val result = mutableListOf<Byte>()
            while (true) {
                val blockSize = stream.readUByte()
                if (blockSize == 0) break
                val buf = ByteArray(blockSize)
                stream.readFully(buf)
                for (b in buf) result.add(b)
            }
            return result.toByteArray()
        }

        private fun skipSubBlocks(stream: InputStream) {
            while (true) {
                val blockSize = stream.readUByte()
                if (blockSize == 0) break
                val skipped = stream.skip(blockSize.toLong())
                // Read remaining if skip didn't skip enough
                if (skipped < blockSize) {
                    val remaining = blockSize - skipped.toInt()
                    for (i in 0 until remaining) stream.readUByte()
                }
            }
        }
    }
}
