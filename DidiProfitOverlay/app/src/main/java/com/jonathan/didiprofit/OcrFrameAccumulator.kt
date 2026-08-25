package com.jonathan.didiprofit

/**
 * Compatibility shim retained so older repository uploads still compile.
 *
 * V3.3.2 intentionally does NOT combine OCR frames. The experimental multi-frame
 * reconstruction introduced transient/wrong calculations while carousel cards
 * were moving, so normal analysis is back to one frame at a time.
 */
@Deprecated("No se usa desde V3.3.2")
class OcrFrameAccumulator(
    private val windowMs: Long = 0L,
    private val maxFrames: Int = 1
) {
    fun add(
        lines: List<OcrLine>,
        now: Long = System.currentTimeMillis()
    ): List<OcrLine> = lines

    fun clear() = Unit
}
