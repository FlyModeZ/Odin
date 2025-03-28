package me.odinmain.features.impl.floor7.p3

import io.github.moulberry.notenoughupdates.NEUApi
import me.odinmain.events.impl.GuiEvent
import me.odinmain.events.impl.TerminalEvent
import me.odinmain.features.Category
import me.odinmain.features.Module
import me.odinmain.features.impl.floor7.p3.termGUI.CustomTermGui
import me.odinmain.features.settings.AlwaysActive
import me.odinmain.features.settings.Setting.Companion.withDependency
import me.odinmain.features.settings.impl.*
import me.odinmain.ui.clickgui.util.ColorUtil
import me.odinmain.ui.clickgui.util.ColorUtil.withAlpha
import me.odinmain.ui.util.MouseUtils
import me.odinmain.utils.equalsOneOf
import me.odinmain.utils.noControlCodes
import me.odinmain.utils.postAndCatch
import me.odinmain.utils.render.Color
import me.odinmain.utils.render.getMCTextWidth
import me.odinmain.utils.render.mcText
import me.odinmain.utils.render.translate
import me.odinmain.utils.skyblock.ClickType
import me.odinmain.utils.skyblock.PlayerUtils.windowClick
import me.odinmain.utils.skyblock.devMessage
import me.odinmain.utils.skyblock.modMessage
import me.odinmain.utils.skyblock.unformattedName
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C0DPacketCloseWindow
import net.minecraft.network.play.client.C0EPacketClickWindow
import net.minecraft.network.play.server.S2DPacketOpenWindow
import net.minecraft.network.play.server.S2EPacketCloseWindow
import net.minecraft.network.play.server.S2FPacketSetSlot
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.input.Keyboard
import java.util.concurrent.CopyOnWriteArrayList

@AlwaysActive // So it can be used in other modules
object TerminalSolver : Module(
    name = "Terminal Solver",
    description = "Renders solution for terminals in floor 7.",
    category = Category.FLOOR7
) {
    val renderType by SelectorSetting("Mode", "Odin", arrayListOf("Odin", "Skytils", "SBE", "Custom GUI"), description = "How the terminal solver should render.")
    val customGuiText by SelectorSetting("Custom Gui Title", "Top Left", arrayListOf("Top Left", "Middle", "Disabled"), description = "Where the custom gui text should be rendered.").withDependency { renderType == 3 }
    val customScale by NumberSetting("Custom Scale", 1f, .8f, 2.5f, .1f, description = "Size of the Custom Terminal Gui.").withDependency { renderType == 3 }
    private val cancelToolTip by BooleanSetting("Stop Tooltips", true, description = "Stops rendering tooltips in terminals.").withDependency { renderType != 3 }
    val hideClicked by BooleanSetting("Hide Clicked", false, description = "Visually hides your first click before a gui updates instantly to improve perceived response time. Does not affect actual click time.")
    private val middleClickGUI by BooleanSetting("Middle Click GUI", true, description = "Replaces right click with middle click in terminals.").withDependency { renderType != 3 }
    private val blockIncorrectClicks by BooleanSetting("Block Incorrect Clicks", true, description = "Blocks incorrect clicks in terminals.").withDependency { renderType != 3 }
    private val cancelMelodySolver by BooleanSetting("Stop Melody Solver", false, description = "Stops rendering the melody solver.")
    val showNumbers by BooleanSetting("Show Numbers", true, description = "Shows numbers in the order terminal.")

    private val showRemoveWrongSettings by DropdownSetting("Render Wrong Settings").withDependency { renderType == 1 }
    private val removeWrong by BooleanSetting("Stop Rendering Wrong", true, description = "Main toggle for stopping the rendering of incorrect items in terminals.").withDependency { renderType == 1 && showRemoveWrongSettings }
    private val removeWrongPanes by BooleanSetting("Stop Panes", true, description = "Stops rendering wrong panes in the panes terminal.").withDependency { renderType == 1 && showRemoveWrongSettings && removeWrong }
    private val removeWrongRubix by BooleanSetting("Stop Rubix", true, description = "Stops rendering wrong colors in the rubix terminal.").withDependency { renderType == 1 && showRemoveWrongSettings && removeWrong }
    private val removeWrongStartsWith by BooleanSetting("Stop Starts With", true, description = "Stops rendering wrong items in the starts with terminal.").withDependency { renderType == 1 && showRemoveWrongSettings && removeWrong }
    private val removeWrongSelect by BooleanSetting("Stop Select", true, description = "Stops rendering wrong items in the select terminal.").withDependency { renderType == 1 && showRemoveWrongSettings && removeWrong }
    private val removeWrongMelody by BooleanSetting("Stop Melody", true, description = "Stops rendering wrong items in the melody terminal.").withDependency { renderType == 1 && showRemoveWrongSettings && removeWrong }

    val gap: Int by NumberSetting("Gap", 10, 0, 20, 1, false, "Gap between items for the custom gui.").withDependency { renderType == 3 }
    val textScale: Int by NumberSetting("Text Scale", 1, 1, 3, increment = 1, description = "Scale of the text in the custom gui.").withDependency { renderType == 3 }

    private val showColors by DropdownSetting("Color Settings")
    private val backgroundColor by ColorSetting("Background Color", Color(45, 45, 45), true, description = "Background color of the terminal solver.").withDependency { renderType == 0 && showColors }

    val customGuiColor by ColorSetting("Custom Gui Color", ColorUtil.moduleButtonColor.withAlpha(.8f), true, description = "Color of the custom gui.").withDependency { renderType == 3 && showColors }
    val panesColor by ColorSetting("Panes Color", Color(0, 170, 170), true, description = "Color of the panes terminal solver.").withDependency { showColors }

    val rubixColor1 by ColorSetting("Rubix Color 1", Color(0, 170, 170), true, description = "Color of the rubix terminal solver for 1 click.").withDependency { showColors }
    val rubixColor2 by ColorSetting("Rubix Color 2", Color(0, 100, 100), true, description = "Color of the rubix terminal solver for 2 click.").withDependency { showColors }
    val oppositeRubixColor1 by ColorSetting("Rubix Color -1", Color(170, 85, 0), true, description = "Color of the rubix terminal solver for -1 click.").withDependency { showColors }
    val oppositeRubixColor2 by ColorSetting("Rubix Color -2", Color(210, 85, 0), true, description = "Color of the rubix terminal solver for -2 click.").withDependency { showColors }

    val orderColor by ColorSetting("Order Color 1", Color(0, 170, 170, 1f), true, description = "Color of the order terminal solver for 1st item.").withDependency { showColors }
    val orderColor2 by ColorSetting("Order Color 2", Color(0, 100, 100, 1f), true, description = "Color of the order terminal solver for 2nd item.").withDependency { showColors }
    val orderColor3 by ColorSetting("Order Color 3", Color(0, 65, 65, 1f), true, description = "Color of the order terminal solver for 3rd item.").withDependency { showColors }

    val startsWithColor by ColorSetting("Starts With Color", Color(0, 170, 170), true, description = "Color of the starts with terminal solver.").withDependency { showColors }

    val selectColor by ColorSetting("Select Color", Color(0, 170, 170), true, description = "Color of the select terminal solver.").withDependency { showColors }

    val melodyColumColor by ColorSetting("Melody Column Color", Color.PURPLE.withAlpha(0.75f), true, description = "Color of the colum indicator for melody.").withDependency { showColors && !cancelMelodySolver }
    val melodyRowColor by ColorSetting("Melody Row Color", Color.GREEN.withAlpha(0.75f), true, description = "Color of the row indicator for melody.").withDependency { showColors && !cancelMelodySolver }
    val melodyPressColumColor by ColorSetting("Melody Press Column Color", Color.YELLOW.withAlpha(0.75f), true, description = "Color of the location for pressing for melody.").withDependency { showColors && !cancelMelodySolver }
    val melodyPressColor by ColorSetting("Melody Press Color", Color.CYAN.withAlpha(0.75f), true, description = "Color of the location for pressing for melody.").withDependency { showColors && !cancelMelodySolver }
    val melodyCorrectRowColor by ColorSetting("Melody Correct Row Color", Color.WHITE.withAlpha(0.75f), true, description = "Color of the whole row for melody.").withDependency { showColors && !cancelMelodySolver }

    data class Terminal(
        val type: TerminalTypes,
        val solution: CopyOnWriteArrayList<Int> = CopyOnWriteArrayList(),
        val items: Array<ItemStack?> = emptyArray(),
        val guiName: String = "",
        val timeOpened: Long = System.currentTimeMillis(),
        var clickedSlot: Pair<Int, Long>? = null

    )
    var currentTerm = Terminal(TerminalTypes.NONE)
        private set
    var lastTermOpened = Terminal(TerminalTypes.NONE)
    private var lastRubixSolution: Int? = null

    init {
        onPacket<S2DPacketOpenWindow> { packet ->
            val windowName = packet.windowTitle?.formattedText?.noControlCodes ?: return@onPacket
            val newTermType = TerminalTypes.entries.find { terminal -> windowName.startsWith(terminal.guiName) }?.takeIf { it != TerminalTypes.NONE } ?: return@onPacket

            if (windowName != currentTerm.guiName) {
                currentTerm = Terminal(type = newTermType, guiName = windowName, items = arrayOfNulls(newTermType.size))
                devMessage("§aNew terminal: §6${currentTerm.type.name}")
                TerminalEvent.Opened(currentTerm).postAndCatch()
                lastTermOpened = currentTerm.copy()
                lastRubixSolution = null
            }
            if (renderType == 3 && Loader.instance().activeModList.any { it.modId == "notenoughupdates" }) NEUApi.setInventoryButtonsToDisabled()
        }

        onPacket<S2FPacketSetSlot> { event ->
            if (currentTerm.type == TerminalTypes.NONE || event.func_149173_d() !in 0 until currentTerm.type.size || event.func_149174_e() == null) return@onPacket
            currentTerm.apply {
                clickedSlot = null
                items[event.func_149173_d()] = event.func_149174_e()
                when (type) {
                    TerminalTypes.PANES -> {
                        if (event.func_149174_e().metadata == 14) {
                            if (event.func_149173_d() !in solution) solution.add(event.func_149173_d())
                        } else solution.remove(event.func_149173_d())
                    }
                    TerminalTypes.RUBIX -> {
                        if (event.func_149173_d() == 32) {
                            solution.clear()
                            solution.addAll(solveColor(items))
                        }
                    }
                    TerminalTypes.ORDER -> {
                        if (event.func_149174_e().metadata == 14 && Item.getIdFromItem(event.func_149174_e().item) == 160) {
                            if (event.func_149173_d() !in solution) solution.add(event.func_149173_d())
                        } else solution.remove(event.func_149173_d())
                        solution.sortBy { items[it]?.stackSize }
                    }
                    TerminalTypes.STARTS_WITH -> {
                        val letter = Regex("What starts with: '(\\w+)'?").find(guiName)?.groupValues?.get(1) ?: return@onPacket modMessage("Failed to find letter, please report this!")

                        if (event.func_149174_e().unformattedName.startsWith(letter, true) && !event.func_149174_e().isItemEnchanted) {
                            if (event.func_149173_d() !in solution) solution.add(event.func_149173_d())
                        } else solution.remove(event.func_149173_d())
                    }
                    TerminalTypes.SELECT -> {
                        val colorNeeded = EnumDyeColor.entries.find { guiName.contains(it.name.replace("_", " ").uppercase()) }?.unlocalizedName ?: return@onPacket modMessage("Failed to find color, please report this!")

                        if (isCorrectItem(event.func_149174_e(), colorNeeded)) {
                            if (event.func_149173_d() !in solution) solution.add(event.func_149173_d())
                        } else solution.remove(event.func_149173_d())
                    }
                    TerminalTypes.MELODY -> {
                        solution.clear()
                        solution.addAll(solveMelody(items))
                    }
                    else -> return@onPacket modMessage("This shouldn't be possible, please report this!")
                }
            }
        }

        onPacket<C0DPacketCloseWindow> {
            leftTerm()
        }

        onPacket<S2EPacketCloseWindow> {
            leftTerm()
        }

        onPacket<C0EPacketClickWindow> { packet ->
            if (currentTerm.clickedSlot?.second?.let { System.currentTimeMillis() - it < 600 } != true) currentTerm.clickedSlot = packet.slotId to System.currentTimeMillis()
        }

        onMessage(Regex("(.{1,16}) activated a terminal! \\((\\d)/(\\d)\\)")) { message ->
            if (message.groupValues[1] == mc.thePlayer.name) TerminalEvent.Solved(lastTermOpened).postAndCatch()
        }

        execute(50) {
            if (enabled && currentTerm.clickedSlot?.second?.let { System.currentTimeMillis() - it > 600 } == true) currentTerm.clickedSlot = null
        }
    }

    @SubscribeEvent
    fun onGuiRender(event: GuiEvent.DrawGuiBackground) {
        if (!enabled || currentTerm.type == TerminalTypes.NONE || (currentTerm.type == TerminalTypes.MELODY && cancelMelodySolver)) return
        when (renderType) {
            0 -> {
                GlStateManager.translate(event.guiLeft.toFloat(), event.guiTop.toFloat(), 399f)
                Gui.drawRect(7, 16, event.xSize - 7, event.ySize - 96, backgroundColor.rgba)
                GlStateManager.translate(-event.guiLeft.toFloat(), -event.guiTop.toFloat(), -399f)
            }
            3 -> {
                CustomTermGui.render()
                event.isCanceled = true
            }
        }
    }

    private fun getShouldBlockWrong(): Boolean {
        if (renderType.equalsOneOf(0, 3)) return true
        if (!removeWrong || renderType == 2) return false
        return when (currentTerm.type) {
            TerminalTypes.PANES -> removeWrongPanes
            TerminalTypes.RUBIX -> removeWrongRubix
            TerminalTypes.ORDER -> true
            TerminalTypes.STARTS_WITH -> removeWrongStartsWith
            TerminalTypes.SELECT -> removeWrongSelect
            TerminalTypes.MELODY -> removeWrongMelody
            else -> false
        }
    }

    @SubscribeEvent
    fun drawSlot(event: GuiEvent.DrawSlot) {
        if (!enabled || renderType == 3 || currentTerm.type == TerminalTypes.NONE || (currentTerm.type == TerminalTypes.MELODY && cancelMelodySolver)) return

        val slotIndex = event.slot.slotIndex
        val inventorySize = event.gui.inventorySlots?.inventorySlots?.size ?: 0

        if (slotIndex !in currentTerm.solution && slotIndex <= inventorySize - 37 && getShouldBlockWrong() && event.slot.inventory !is InventoryPlayer) event.isCanceled = true
        if (slotIndex !in currentTerm.solution || slotIndex > inventorySize - 37 || event.slot.inventory is InventoryPlayer) return

        val zLevel = if (renderType == 2 && currentTerm.type.equalsOneOf(TerminalTypes.STARTS_WITH, TerminalTypes.SELECT)) 0f else 400f

        translate(0f, 0f, zLevel)
        GlStateManager.disableLighting()
        GlStateManager.enableDepth()

        val cancel = slotIndex == currentTerm.clickedSlot?.first && hideClicked

        when (currentTerm.type) {
            TerminalTypes.PANES -> if (renderType != 1 && !cancel) Gui.drawRect(event.x, event.y, event.x + 16, event.y + 16, panesColor.rgba)

            TerminalTypes.RUBIX -> {
                val needed = currentTerm.solution.count { it == slotIndex }
                val realNeeded = if (!cancel) needed else when (needed) {
                    3 -> 4
                    4 -> 0
                    else -> needed - 1
                }

                if (realNeeded != 0 && renderType != -1) {
                    val text = if (needed < 3) realNeeded else (realNeeded - 5)
                    val color = when (text) {
                        2 -> rubixColor2
                        1 -> rubixColor1
                        -2 -> oppositeRubixColor2
                        else -> oppositeRubixColor1
                    }

                    if (renderType != 1) Gui.drawRect(event.x, event.y, event.x + 16, event.y + 16, color.rgba)
                    mcText(text.toString(), event.x + 8f - getMCTextWidth(text.toString()) / 2, event.y + 4.5, 1, Color.WHITE, center = false)
                }
            }
            TerminalTypes.ORDER -> {
                val index = currentTerm.solution.indexOf(event.slot.slotIndex) + if (currentTerm.clickedSlot != null && hideClicked) -1 else 0
                if (index != -1) {
                    if (index < 3) {
                        val color = when (index) {
                            0 -> orderColor
                            1 -> orderColor2
                            else -> orderColor3
                        }.rgba
                        Gui.drawRect(event.x, event.y, event.x + 16, event.y + 16, color)
                        event.isCanceled = true
                    }
                    val amount = event.slot.stack?.stackSize?.toString() ?: ""
                    if (showNumbers) mcText(amount, event.x + 8.5f - getMCTextWidth(amount) / 2, event.y + 4.5f, 1, Color.WHITE, center = false)
                }
            }
            TerminalTypes.STARTS_WITH, TerminalTypes.SELECT ->
                if (!cancel && (renderType != 1 || (renderType == 1 && !removeWrong))) Gui.drawRect(event.x, event.y, event.x + 16, event.y + 16, startsWithColor.rgba)

            TerminalTypes.MELODY -> if (renderType != 1 || (renderType == 1 && !removeWrong)) {
                val colorMelody = when {
                    event.slot.stack?.metadata == 5 && Item.getIdFromItem(event.slot.stack.item) == 160 -> melodyRowColor
                    event.slot.stack?.metadata == 2 && Item.getIdFromItem(event.slot.stack.item) == 160 -> melodyColumColor
                    else -> melodyPressColor
                }.rgba
                Gui.drawRect(event.x, event.y, event.x + 16, event.y + 16, colorMelody)
            }
            else -> {}
        }
        GlStateManager.enableLighting()
        translate(0f, 0f, -zLevel)
    }

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        if (cancelToolTip && enabled && currentTerm.type != TerminalTypes.NONE) event.toolTip.clear()
    }

    @SubscribeEvent(receiveCanceled = true, priority = EventPriority.HIGHEST)
    fun onGuiClick(event: GuiEvent.MouseClick) {
        if (!enabled || currentTerm.type == TerminalTypes.NONE) return

        if (renderType == 3 && !(currentTerm.type == TerminalTypes.MELODY && cancelMelodySolver)) {
            CustomTermGui.mouseClicked(MouseUtils.mouseX.toInt(), MouseUtils.mouseY.toInt(), event.button)
            event.isCanceled = true
            return
        }

        val slotIndex = (event.gui as? GuiChest)?.slotUnderMouse?.slotIndex ?: return

        if (blockIncorrectClicks && !canClick(slotIndex, event.button)) {
            event.isCanceled = true
            return
        }

        if (middleClickGUI) {
            windowClick(slotIndex, if (event.button == 0) ClickType.Middle else ClickType.Right)
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onGuiKeyPress(event: GuiEvent.KeyPress) {
        if (!enabled || currentTerm.type == TerminalTypes.NONE || (currentTerm.type == TerminalTypes.MELODY && cancelMelodySolver)) return
        if (renderType == 3 && (Keyboard.isKeyDown(mc.gameSettings.keyBindDrop.keyCode) || (event.key in 2..10))) {
            CustomTermGui.mouseClicked(MouseUtils.mouseX.toInt(), MouseUtils.mouseY.toInt(), if (event.key == Keyboard.KEY_LCONTROL && event.key == mc.gameSettings.keyBindDrop.keyCode) 1 else 0)
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun itemStack(event: GuiEvent.DrawSlotOverlay) {
        if (enabled && currentTerm.type == TerminalTypes.ORDER && (event.stack?.item?.registryName ?: return) == "minecraft:stained_glass_pane") event.isCanceled = true
    }

    private fun leftTerm() {
        if (currentTerm.type == TerminalTypes.NONE) return
        devMessage("§cLeft terminal: §6${currentTerm.type.name}")
        TerminalEvent.Closed(currentTerm).postAndCatch()
        currentTerm = Terminal(TerminalTypes.NONE)
    }

    fun canClick(slotIndex: Int, button: Int, needed: Int = currentTerm.solution.count { it == slotIndex }): Boolean = when {
        currentTerm.type == TerminalTypes.MELODY -> true
        slotIndex !in currentTerm.solution -> false
        currentTerm.type == TerminalTypes.ORDER && slotIndex != currentTerm.solution.firstOrNull() -> false
        currentTerm.type == TerminalTypes.RUBIX && ((needed < 3 && button != 0) || (needed >= 3 && button != 1)) -> false
        else -> true
    }

    private val colorOrder = listOf(1, 4, 13, 11, 14)
    private fun solveColor(items: Array<ItemStack?>): List<Int> {
        val panes = items.mapNotNull { item -> if (item?.metadata != 15 && Item.getIdFromItem(item?.item) == 160) item else null }
        var temp = List(100) { i -> i }
        if (lastRubixSolution != null) {
            temp = panes.flatMap { pane ->
                if (pane.metadata != lastRubixSolution) Array(dist(colorOrder.indexOf(pane.metadata), colorOrder.indexOf(lastRubixSolution))) { pane }.toList()
                else emptyList()
            }.map { items.indexOf(it) }
        } else {
            for (color in colorOrder) {
                val temp2 = panes.flatMap { pane ->
                    if (pane.metadata != color) Array(dist(colorOrder.indexOf(pane.metadata), colorOrder.indexOf(color))) { pane }.toList()
                    else emptyList()
                }.map { items.indexOf(it) }
                if (getRealSize(temp2) < getRealSize(temp)) {
                    temp = temp2
                    lastRubixSolution = color
                }
            }
        }
        return temp
    }

    private fun getRealSize(list: List<Int>): Int {
        var size = 0
        list.distinct().forEach { pane ->
            val count = list.count { it == pane }
            size += if (count >= 3) 5 - count else count
        }
        return size
    }

    private fun dist(pane: Int, most: Int): Int =
        if (pane > most) (most + colorOrder.size) - pane else most - pane

    private fun isCorrectItem(item: ItemStack?, color: String): Boolean {
        return item?.isItemEnchanted == false &&
                Item.getIdFromItem(item.item) != 160 &&
                item.unlocalizedName?.contains(color, true) == true &&
                (color == "lightBlue" || item.unlocalizedName?.contains("lightBlue", true) == false) // color BLUE should not accept light blue items.
    }

    private fun solveMelody(items: Array<ItemStack?>): List<Int> {
        val greenPane = items.indexOfLast { it?.metadata == 5 && Item.getIdFromItem(it.item) == 160 }.takeIf { it != -1 } ?: return emptyList()
        val magentaPane = items.indexOfFirst { it?.metadata == 2 && Item.getIdFromItem(it.item) == 160 }.takeIf { it != -1 } ?: return emptyList()
        val greenClay = items.indexOfLast { it?.metadata == 5 && Item.getIdFromItem(it.item) == 159 }.takeIf { it != -1 } ?: return emptyList()
        return items.mapIndexedNotNull { index, item ->
            when {
                index == greenPane || item?.metadata == 2 && Item.getIdFromItem(item.item) == 160 -> index
                index == greenClay && greenPane % 9 == magentaPane % 9 -> index
                else -> null
            }
        }
    }
}