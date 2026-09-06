package de.jo_field.cleanchat.gui

import de.jo_field.cleanchat.config.CleanChatConfig
import de.jo_field.cleanchat.config.FilterAction
import de.jo_field.cleanchat.config.FilterMode
import de.jo_field.cleanchat.config.FilterRule
import de.jo_field.cleanchat.filter.MessageFilter
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/** Collapsible tester and continuously scrolling, clipped rule editor. */
class CleanChatScreen(private val parent: Screen?) : Screen(Component.literal("Clean Chat")) {
    private var margin = 10
    private val white = 0xFFFFFFFF.toInt()
    private val muted = 0xFFAAAAAA.toInt()
    private val accent = 0xFF55AAFF.toInt()
    private val green = 0xFF55FF55.toInt()
    private val red = 0xFFFF5555.toInt()
    private val panelColor = 0xD91C1C1C.toInt()
    private val borderColor = 0xFF505050.toInt()
    private val viewport = RuleViewport()

    // State lives outside the widgets so rebuilding never discards test input.
    private var expanded = true
    private var testMessage = "${Minecraft.getInstance().player?.name?.string ?: "Notch"} was teleported to Steve"
    private var testPattern = "([A-Za-z0-9_]{3,16}) was teleported to ([A-Za-z0-9_]{3,16})"
    private var testMode = FilterMode.REGEX
    private var draggingScrollbar = false
    private var thumbGrab = 0.0
    private var addTestButton: Button? = null
    private val fixedWidgets = mutableListOf<AbstractWidget>()

    private data class Row(val rule: FilterRule, val widgets: List<Pair<AbstractWidget, Int>>)

    private val rows = mutableListOf<Row>()
    private var compact = false
    private var dense = false
    private var controlHeight = 20
    private var narrow = false
    private var messageTop = 0
    private var patternTop = 0
    private var resultTop = 0
    private var rulesHeaderTop = 0
    private var listTop = 0
    private var listBottom = 0
    private var listRight = 0
    private var rowHeight = 30
    private var bannerTop = 0
    private var bannerHeight = 0
    private var footerTop = 0
    private var messageHeight = 0
    private var patternHeight = 0
    private var resultHeight = 0
    private var rowLeft = 0
    private var patternX = 0
    private var patternWidth = 0
    private var modeX = 0
    private var modeWidth = 0
    private var actionX = 0
    private var actionWidth = 0

    private enum class ButtonStyle {
        FLAT,
        ACCENT,
        PRIMARY,
        BANNER,
        POWER,
        CHECKBOX,
        SELECT,
        DELETE
    }

    private enum class Icon {
        TUBE,
        POWER,
        FILTER,
        SAVE,
        BACK,
        CHECK,
        CROSS
    }

    override fun init() {
        fixedWidgets.clear()
        rows.clear()
        addTestButton = null
        draggingScrollbar = false
        compact = height < 420
        dense = height < 320
        controlHeight = if (dense) 16 else 20
        narrow = width < 660
        margin = (width / 24).coerceIn(10, 32)
        rowHeight = if (narrow) controlHeight * 2 + 12 else controlHeight + 8
        bannerTop = when {
            dense -> 32; compact -> 40; else -> 48
        }
        bannerHeight = when {
            dense -> 22; compact -> 28; else -> 36
        }
        footerTop = height - when {
            dense -> 26; compact -> 30; else -> 38
        }
        listBottom = footerTop - if (dense) 6 else 8
        listRight = width - margin - 14
        messageHeight = when {
            dense -> 30; compact -> 42; else -> 60
        }
        patternHeight = when {
            dense -> 40; compact -> 54; else -> 66
        }
        resultHeight = when {
            dense -> 24; compact -> 30; else -> 38
        }
        val gap = when {
            dense -> 2; compact -> 4; else -> 6
        }
        messageTop = bannerTop + bannerHeight + gap
        patternTop = messageTop + messageHeight + gap
        resultTop = patternTop + patternHeight + gap
        val rulesHeaderHeight = when {
            dense -> 30; compact -> 40; else -> 44
        }
        rulesHeaderTop = if (expanded) resultTop + resultHeight + gap else bannerTop + bannerHeight + gap
        // Keep controls usable on very short windows, without leaving a hidden tester's space behind.
        if (expanded && listBottom - rulesHeaderTop - rulesHeaderHeight < controlHeight + 4) {
            expanded = false
            rulesHeaderTop = bannerTop + bannerHeight + gap
        }
        listTop = rulesHeaderTop + rulesHeaderHeight
        layoutRuleColumns()

        fixedWidgets += button(10, if (dense) 7 else 10, 60, "Back", icon = Icon.BACK) { onClose() }
        fixedWidgets += button(
            width - 110, if (dense) 4 else 7, 100, enabledLabel(), ButtonStyle.POWER,
            buttonHeight = if (dense) 24 else 28
        ) {
            CleanChatConfig.modEnabled = !CleanChatConfig.modEnabled
            label(it, enabledLabel())
        }
        fixedWidgets += button(
            margin, bannerTop, width - margin * 2, "Filter tester",
            ButtonStyle.BANNER, buttonHeight = bannerHeight
        ) {
            expanded = !expanded
            rebuildWidgets()
        }.also {
            it.setTooltip(Tooltip.create(Component.literal(if (expanded) "Collapse filter tester" else "Expand filter tester")))
        }
        // No tester fields or result widgets exist while the banner is collapsed.
        if (expanded) buildTester()
        fixedWidgets += button(
            width - margin - 100,
            rulesHeaderTop + if (dense) 3 else 5,
            92,
            "+ Add rule",
            ButtonStyle.ACCENT
        ) {
            addRule(FilterRule())
        }
        fixedWidgets += button(width - margin - 86, footerTop + 5, 86, "Save", ButtonStyle.PRIMARY, Icon.SAVE) {
            CleanChatConfig.save()
            onClose()
        }
        CleanChatConfig.rules.forEach { rows += buildRuleRow(it) }
        viewport.resize(rows.size * rowHeight, listBottom - listTop)
        positionRows()
    }

    private fun enabledLabel() = if (CleanChatConfig.modEnabled) "Clean Chat: ON" else "Clean Chat: OFF"
    private fun label(button: Button, text: String) {
        button.message = Component.literal(text)
        button.setTooltip(Tooltip.create(button.message))
    }

    private fun button(
        x: Int, y: Int, w: Int, text: String, style: ButtonStyle = ButtonStyle.FLAT,
        icon: Icon? = null, buttonHeight: Int = controlHeight, action: (Button) -> Unit
    ): Button =
        addWidget(StyledButton(x, y, w.coerceAtLeast(1), buttonHeight, text, style, icon, action)).also {
            it.setTooltip(Tooltip.create(Component.literal(text)))
        }

    private inner class StyledButton(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        text: String,
        private val style: ButtonStyle,
        private val icon: Icon?,
        action: (Button) -> Unit
    ) : Button(
        x, y, w, h, Component.literal(text), OnPress { action(it) },
        CreateNarration { it.get() }) {
        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            val highlighted = active && (isHovered || isFocused)
            val background = when (style) {
                ButtonStyle.PRIMARY -> if (highlighted) 0xFF397ACA.toInt() else 0xFF285797.toInt()
                ButtonStyle.BANNER -> if (highlighted) 0xED30343A.toInt() else panelColor
                else -> if (highlighted) 0xED30343A.toInt() else 0xDC101112.toInt()
            }
            val border = when {
                !active -> 0xFF363636.toInt()
                highlighted || style == ButtonStyle.ACCENT || style == ButtonStyle.PRIMARY -> accent
                else -> borderColor
            }
            graphics.fill(x, y, right, bottom, background)
            graphics.outline(x, y, width, height, border)
            val color = if (active) white else muted
            when (style) {
                ButtonStyle.BANNER -> {
                    graphics.fill(x, y, x + 2, bottom, accent)
                    val tileSize = minOf(24, height - 4)
                    val tileY = y + (height - tileSize) / 2
                    graphics.fill(x + 8, tileY, x + 8 + tileSize, tileY + tileSize, 0xED101010.toInt())
                    graphics.outline(x + 8, tileY, tileSize, tileSize, 0xFF080808.toInt())
                    drawIcon(graphics, Icon.TUBE, x + 8 + (tileSize - 16) / 2, tileY + (tileSize - 16) / 2, accent)
                    clippedText(
                        graphics,
                        "Filter tester",
                        x + 42,
                        y + if (height > 30) 7 else (height - 8) / 2,
                        width - 70,
                        white
                    )
                    if (height > 30)
                        clippedText(graphics, "Test messages and rules live", x + 42, y + 21, width - 70, muted)
                    drawChevron(graphics, right - 20, y + height / 2 - 2, expanded, accent)
                }

                ButtonStyle.POWER -> {
                    drawIcon(
                        graphics,
                        Icon.POWER,
                        x + 9,
                        y + (height - 16) / 2,
                        if (CleanChatConfig.modEnabled) accent else muted
                    )
                    clippedText(graphics, "Clean Chat", x + 31, y + 4, width - 35, white)
                    clippedText(
                        graphics, if (CleanChatConfig.modEnabled) "ON" else "OFF", x + 31, y + height - 11,
                        width - 35, if (CleanChatConfig.modEnabled) green else muted
                    )
                }

                ButtonStyle.CHECKBOX -> if (message.string == "[x]")
                    drawIcon(
                        graphics,
                        Icon.CHECK,
                        x + (width - 16) / 2,
                        y + (height - 16) / 2,
                        if (active) accent else muted
                    )

                ButtonStyle.DELETE -> drawIcon(
                    graphics, Icon.CROSS, x + (width - 16) / 2,
                    y + (height - 16) / 2, if (highlighted) red else color
                )

                ButtonStyle.SELECT -> {
                    clippedText(graphics, message.string, x + 6, y + (height - 8) / 2, width - 24, color)
                    drawChevron(graphics, right - 14, y + height / 2 - 2, false, color)
                }

                else -> {
                    val iconSpace = if (icon == null) 0 else 20
                    val text = font.plainSubstrByWidth(message.string, (width - iconSpace - 10).coerceAtLeast(0))
                    val textX = x + ((width - font.width(text) - iconSpace) / 2).coerceAtLeast(4)
                    if (icon != null) drawIcon(graphics, icon, textX, y + (height - 16) / 2, color)
                    graphics.text(
                        font, text, textX + iconSpace, y + (height - 8) / 2,
                        if (style == ButtonStyle.ACCENT && active) accent else color, false
                    )
                }
            }
        }
    }

    private fun editBox(
        x: Int,
        y: Int,
        w: Int,
        text: String,
        value: String,
        maxLength: Int = 4096,
        changed: (String) -> Unit
    ): EditBox {
        val box = addWidget(EditBox(font, x, y, w.coerceAtLeast(1), controlHeight, Component.literal(text)))
        box.setMaxLength(maxLength)
        box.value = value
        box.setResponder(changed)
        box.setTooltip(Tooltip.create(Component.literal(text)))
        return box
    }

    private fun buildTester() {
        val x = margin + 8
        val innerWidth = width - x * 2
        val fieldBottomGap = if (dense) 2 else 6
        fixedWidgets += editBox(
            x, messageTop + messageHeight - controlHeight - fieldBottomGap, innerWidth,
            "Chat message to test", testMessage, 1024
        ) { testMessage = it }
        val modeWidth = if (narrow) 112 else 120
        val fieldY = patternTop + patternHeight - controlHeight - fieldBottomGap
        fixedWidgets += editBox(x, fieldY, innerWidth - modeWidth - 6, "Filter pattern", testPattern) {
            testPattern = it
            addTestButton?.active = it.isNotEmpty()
        }
        fixedWidgets += button(
            x + innerWidth - modeWidth,
            fieldY,
            modeWidth,
            testMode.displayName,
            ButtonStyle.SELECT
        ) {
            testMode = testMode.next()
            label(it, testMode.displayName)
        }
        addTestButton = button(
            x + innerWidth - modeWidth,
            patternTop + if (dense) 2 else 4,
            modeWidth,
            "Add current filter", ButtonStyle.ACCENT
        ) {
            if (testPattern.isNotEmpty())
                addRule(FilterRule(pattern = testPattern, mode = testMode, action = FilterAction.HIDE))
        }.also {
            it.active = testPattern.isNotEmpty()
            fixedWidgets += it
        }
    }

    private fun addRule(rule: FilterRule) {
        CleanChatConfig.addRule(rule)
        rebuildWidgets()
        viewport.scrollTo(viewport.maxOffset)
        positionRows()
    }

    private fun layoutRuleColumns() {
        rowLeft = margin + 8
        val available = listRight - rowLeft
        modeWidth = if (narrow) (available - 62) * 45 / 100 else 124
        actionWidth = if (narrow) available - 62 - modeWidth else 148
        modeX = if (narrow) rowLeft + 28 else listRight - 22 - 12 - modeWidth - actionWidth
        actionX = modeX + modeWidth + 6
        patternX = if (narrow) rowLeft else rowLeft + 44
        patternWidth = if (narrow) available else modeX - patternX - 6
    }

    private fun buildRuleRow(rule: FilterRule): Row {
        val widgets = mutableListOf<Pair<AbstractWidget, Int>>()
        val controlsY = if (narrow) controlHeight + 8 else 4
        widgets += editBox(patternX, 4, patternWidth, "Filter pattern", rule.pattern) {
            rule.pattern = it
        } to 4
        widgets += button(
            rowLeft,
            controlsY,
            22,
            if (rule.enabled) "[x]" else "[ ]",
            ButtonStyle.CHECKBOX
        ) {
            rule.enabled = !rule.enabled
            label(it, if (rule.enabled) "[x]" else "[ ]")
            it.setTooltip(Tooltip.create(Component.literal(if (rule.enabled) "Rule enabled" else "Rule disabled")))
        } to controlsY
        widgets += button(modeX, controlsY, modeWidth, rule.mode.displayName, ButtonStyle.SELECT) {
            rule.mode = rule.mode.next()
            label(it, rule.mode.displayName)
        } to controlsY
        widgets += button(actionX, controlsY, actionWidth, rule.action.displayName, ButtonStyle.SELECT) {
            rule.action = rule.action.next()
            label(it, rule.action.displayName)
        } to controlsY
        widgets += button(listRight - 22, controlsY, 22, "Delete rule", ButtonStyle.DELETE) {
            CleanChatConfig.removeRule(rule)
            rebuildWidgets()
        }.also { it.setTooltip(Tooltip.create(Component.literal("Delete rule"))) } to controlsY
        return Row(rule, widgets)
    }

    private fun positionRows() {
        rows.forEachIndexed { index, row ->
            val top = listTop + index * rowHeight - viewport.offset.toInt()
            row.widgets.forEach { (widget, dy) ->
                widget.y = top + dy
                widget.visible = widget.bottom > listTop && widget.y < listBottom
                // Clipped controls cannot receive input outside the list.
                widget.active = widget.y >= listTop && widget.bottom <= listBottom
                if (!widget.active && widget.isFocused) clearFocus()
            }
        }
    }

    private fun inList(x: Double, y: Double) =
        x >= margin && x < width - margin && y >= listTop && y < listBottom

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontal: Double,
        vertical: Double
    ): Boolean {
        if (inList(mouseX, mouseY)) {
            viewport.scrollTo(viewport.offset - vertical * minOf(rowHeight, (viewport.height / 2).coerceAtLeast(1)))
            positionRows()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0 && viewport.maxOffset > 0 && inList(
                event.x(),
                event.y()
            ) && event.x() >= listRight + 3
        ) {
            clearFocus()
            val thumbY = listTop + viewport.thumbTop
            thumbGrab = if (event.y() >= thumbY && event.y() < thumbY + viewport.thumbHeight)
                event.y() - thumbY else viewport.thumbHeight / 2.0
            draggingScrollbar = true
            viewport.dragThumb(event.y() - listTop - thumbGrab)
            positionRows()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (draggingScrollbar && event.button() == 0) {
            viewport.dragThumb(event.y() - listTop - thumbGrab)
            positionRows()
            return true
        }
        return super.mouseDragged(event, deltaX, deltaY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false
            return true
        }
        return super.mouseReleased(event)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xCC101010.toInt())
        val titleAreaLeft = 78
        val titleAreaRight = width - 118
        val titleCenter = (titleAreaLeft + titleAreaRight) / 2
        graphics.text(font, title.string, titleCenter - font.width(title.string) / 2, 9, white, true)
        if (!compact) {
            val subtitle =
                font.plainSubstrByWidth("Chat filter settings", (titleAreaRight - titleAreaLeft).coerceAtLeast(0))
            graphics.text(font, subtitle, titleCenter - font.width(subtitle) / 2, 23, muted, false)
        }
        val headerLineY = bannerTop - if (compact) 3 else 7
        graphics.fill(8, headerLineY, width - 8, headerLineY + 1, borderColor)
        if (expanded) drawTester(graphics)
        panel(graphics, rulesHeaderTop, listBottom - rulesHeaderTop + 4)
        drawIcon(graphics, Icon.FILTER, margin + 10, rulesHeaderTop + if (dense) 2 else 7, accent)
        val rulesTitleY = rulesHeaderTop + if (dense) 7 else 11
        clippedText(graphics, "Filter rules", margin + 34, rulesTitleY, width - margin * 2 - 140, white)
        if (!narrow) {
            val countX = margin + 42 + font.width("Filter rules")
            clippedText(
                graphics, "${rows.size} rules", countX, rulesTitleY,
                width - margin - 108 - countX, muted
            )
        }
        val ruleDividerY = listTop - if (dense) 11 else 15
        graphics.fill(margin, ruleDividerY, width - margin, ruleDividerY + 1, borderColor)
        if (narrow) {
            clippedText(
                graphics,
                "Pattern / Enabled / Mode / Action",
                rowLeft,
                listTop - 10,
                listRight - rowLeft,
                muted
            )
        } else {
            clippedText(graphics, "Enabled", rowLeft, listTop - 10, patternX - rowLeft - 2, muted)
            clippedText(graphics, "Pattern", patternX, listTop - 10, patternWidth, muted)
            clippedText(graphics, "Mode", modeX, listTop - 10, modeWidth, muted)
            clippedText(graphics, "Action", actionX, listTop - 10, actionWidth, muted)
        }
        graphics.fill(8, footerTop, width - 8, footerTop + 1, borderColor)
        clippedText(
            graphics, if (narrow) "Save to keep your changes." else "Changes apply immediately. Save to keep them.",
            margin, footerTop + 11, width - margin * 2 - 102, muted
        )
        fixedWidgets.forEach { it.extractRenderState(graphics, mouseX, mouseY, partialTick) }
        graphics.enableScissor(margin, listTop, listRight, listBottom)
        rows.forEachIndexed { index, row ->
            val y = listTop + index * rowHeight - viewport.offset.toInt()
            if (y < listBottom && y + rowHeight > listTop) {
                graphics.fill(
                    rowLeft - 3, y, listRight, y + rowHeight - 2,
                    if (row.rule.enabled) 0x401E1E1E else 0x60101010
                )
                row.widgets.forEach { (widget, _) -> widget.extractRenderState(graphics, mouseX, mouseY, partialTick) }
            }
        }
        if (rows.isEmpty())
            clippedText(
                graphics,
                "No filter rules yet. Click + Add rule.",
                margin + 4,
                listTop + 10,
                listRight - margin - 8,
                muted
            )
        graphics.disableScissor()
        if (viewport.maxOffset > 0) {
            graphics.fill(listRight + 5, listTop, listRight + 9, listBottom, 0xFF303030.toInt())
            val top = listTop + viewport.thumbTop
            graphics.fill(
                listRight + 4, top, listRight + 10, top + viewport.thumbHeight,
                if (draggingScrollbar) 0xFF8BC7FF.toInt() else accent
            )
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    private fun drawTester(graphics: GuiGraphicsExtractor) {
        panel(graphics, messageTop, messageHeight)
        panel(graphics, patternTop, patternHeight)
        clippedText(
            graphics,
            "CHAT MESSAGE TO TEST",
            margin + 8,
            messageTop + if (dense) 2 else 6,
            width - margin * 2 - 16,
            accent
        )
        clippedText(
            graphics,
            "FILTER PATTERN",
            margin + 8,
            patternTop + if (dense) 3 else 7,
            width - margin * 2 - 178,
            accent
        )
        if (!compact) {
            clippedText(
                graphics, "Enter the Minecraft chat message you want to test:", margin + 8, messageTop + 20,
                width - margin * 2 - 16, muted
            )
            clippedText(
                graphics, "Pattern checked against the message:", margin + 8, patternTop + 27,
                width - margin * 2 - 16, muted
            )
        }
        val match = MessageFilter.matches(FilterRule(pattern = testPattern, mode = testMode), testMessage)
        val resultColor = if (match) green else red
        graphics.fill(
            margin,
            resultTop,
            width - margin,
            resultTop + resultHeight,
            if (match) 0x50225522 else 0x50221111
        )
        graphics.outline(margin, resultTop, width - margin * 2, resultHeight, borderColor)
        graphics.fill(margin, resultTop, margin + 3, resultTop + resultHeight, resultColor)
        graphics.text(font, "TEST RESULT", margin + 10, resultTop + if (dense) 3 else 6, muted, false)
        clippedText(
            graphics,
            if (match) "MATCH - This message would be caught" else "NO MATCH - This message would not be caught",
            margin + 10,
            resultTop + when {
                dense -> 14; compact -> 18; else -> 22
            },
            width - margin * 2 - 20,
            resultColor
        )
    }

    /** Tiny pixel icons are drawn here so the screen needs no texture/resource files. */
    private fun drawIcon(graphics: GuiGraphicsExtractor, icon: Icon, x: Int, y: Int, color: Int) {
        fun rect(left: Int, top: Int, right: Int, bottom: Int, ink: Int = color) {
            graphics.fill(x + left, y + top, x + right, y + bottom, ink)
        }
        when (icon) {
            Icon.TUBE -> {
                rect(6, 1, 12, 3, white)
                rect(7, 3, 8, 7, white)
                rect(10, 3, 11, 7, white)
                rect(6, 6, 8, 8, white)
                rect(10, 6, 12, 8, white)
                rect(5, 8, 6, 14, white)
                rect(12, 8, 13, 14, white)
                rect(6, 14, 12, 16, white)
                rect(6, 10, 12, 14)
                rect(8, 9, 12, 10)
                rect(7, 10, 8, 13, 0xFFB7DFFF.toInt())
                rect(3, 2, 5, 4)
                rect(12, 0, 14, 2)
            }

            Icon.POWER -> {
                rect(7, 0, 9, 8)
                rect(3, 3, 5, 5)
                rect(11, 3, 13, 5)
                rect(1, 5, 3, 11)
                rect(13, 5, 15, 11)
                rect(3, 11, 5, 13)
                rect(11, 11, 13, 13)
                rect(5, 13, 11, 15)
            }

            Icon.FILTER -> {
                rect(0, 1, 16, 3)
                for (i in 0..5) {
                    rect(i, i + 2, i + 2, i + 4)
                    rect(14 - i, i + 2, 16 - i, i + 4)
                }
                rect(6, 8, 9, 15)
            }

            Icon.SAVE -> {
                rect(1, 1, 14, 15)
                rect(4, 1, 11, 6, 0xFF244A78.toInt())
                rect(9, 2, 10, 5)
                rect(4, 9, 12, 14, 0xFF244A78.toInt())
                rect(6, 11, 10, 12)
            }

            Icon.BACK -> for (i in 0..4) {
                rect(8 - i, 3 + i, 10 - i, 5 + i)
                rect(4 + i, 7 + i, 6 + i, 9 + i)
            }

            Icon.CHECK -> {
                for (i in 0..3) rect(2 + i, 7 + i, 4 + i, 9 + i)
                for (i in 0..7) rect(5 + i, 10 - i, 7 + i, 12 - i)
            }

            Icon.CROSS -> for (i in 0..7) {
                rect(4 + i, 4 + i, 6 + i, 6 + i)
                rect(11 - i, 4 + i, 13 - i, 6 + i)
            }
        }
    }

    private fun drawChevron(graphics: GuiGraphicsExtractor, x: Int, y: Int, up: Boolean, color: Int) {
        for (i in 0..4) {
            val dy = if (up) 4 - i else i
            graphics.fill(x + i, y + dy, x + i + 2, y + dy + 2, color)
            graphics.fill(x + 8 - i, y + dy, x + 10 - i, y + dy + 2, color)
        }
    }

    private fun clippedText(graphics: GuiGraphicsExtractor, text: String, x: Int, y: Int, available: Int, color: Int) {
        graphics.text(font, font.plainSubstrByWidth(text, available.coerceAtLeast(0)), x, y, color, false)
    }

    private fun panel(graphics: GuiGraphicsExtractor, y: Int, h: Int) {
        graphics.fill(margin, y, width - margin, y + h, panelColor)
        graphics.outline(margin, y, width - margin * 2, h, borderColor)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen() = false
}
