package com.jonathan.didiprofit

import java.util.ArrayDeque
import kotlin.math.abs

class OcrFrameAccumulator(
    private val windowMs: Long = 1400L,
    private val maxFrames: Int = 6
) {
    private data class Frame(
        val at: Long,
        val fareHint: Double?,
        val lines: List<OcrLine>
    )

    private val frames = ArrayDeque<Frame>()
    private var anchorFare: Double? = null

    fun add(lines: List<OcrLine>, now: Long = System.currentTimeMillis()): List<OcrLine> {
        val hint = OfferParser.mainFareHint(lines)

        if (hint != null) {
            val old = anchorFare
            if (old == null || abs(old - hint) > 0.05) {
                frames.clear()
                anchorFare = hint
            }
        }

        frames.addLast(Frame(now, hint, lines))
        prune(now)

        val anchor = anchorFare
        val selected = if (anchor != null) {
            frames.filter { frame ->
                frame.fareHint == null || abs(frame.fareHint - anchor) <= 0.05
            }
        } else {
            frames.toList()
        }

        return selected
            .flatMap { it.lines }
            .distinctBy { line ->
                "${line.text.trim().lowercase()}|${line.centerY / 8}|${line.centerX / 12}"
            }
            .sortedBy { it.centerY }
    }

    fun clear() {
        frames.clear()
        anchorFare = null
    }

    private fun prune(now: Long) {
        while (frames.isNotEmpty() && now - frames.first().at > windowMs) {
            frames.removeFirst()
        }
        while (frames.size > maxFrames) {
            frames.removeFirst()
        }
        if (frames.isEmpty()) anchorFare = null
    }
}
