package de.jo_field.cleanchat.gui

/** Pixel scrolling for wheel input, scrollbar dragging and layout changes. */
class RuleViewport {
    var offset = 0.0
        private set
    var contentHeight = 0
        private set
    var height = 0
        private set
    val maxOffset get() = (contentHeight - height).coerceAtLeast(0).toDouble()
    val thumbHeight get() = if (contentHeight <= height) height else
        (height.toDouble() * height / contentHeight).toInt().coerceIn(minOf(16, height), height)
    val thumbTop get() = if (maxOffset == 0.0) 0 else
        (offset / maxOffset * (height - thumbHeight)).toInt()
    fun resize(contentHeight: Int, height: Int) {
        this.contentHeight = contentHeight.coerceAtLeast(0)
        this.height = height.coerceAtLeast(0)
        scrollTo(offset)
    }
    fun scrollTo(value: Double) { offset = value.coerceIn(0.0, maxOffset) }
    fun dragThumb(top: Double) {
        val travel = height - thumbHeight
        scrollTo(if (travel <= 0) 0.0 else top / travel * maxOffset)
    }
}
