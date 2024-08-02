package me.odinclient.features.impl.skyblock

import me.odinmain.features.Category
import me.odinmain.features.Module
import me.odinmain.features.settings.impl.BooleanSetting
import me.odinmain.utils.fillItemFromSack
import me.odinmain.utils.skyblock.KuudraUtils
import me.odinmain.utils.skyblock.dungeon.DungeonUtils
import me.odinmain.utils.skyblock.itemID

object AutoGFS : Module(
    name = "Auto Gfs",
    description = "Automatically refills certain items from sack.",
    category = Category.DUNGEON
) {
    private val inKuudra: Boolean by BooleanSetting("In Kuudra", true, description = "Only gfs in Kuudra.")
    private val inDungeon: Boolean by BooleanSetting("In Dungeon", true, description = "Only gfs in Dungeons.")
    private val refillOnDungeonStart: Boolean by BooleanSetting("Refill on Dungeon Start", true, description = "Refill when a dungeon starts.")
    private val refillPearl: Boolean by BooleanSetting("Refill Pearl", true, description = "Refill ender pearls.")
    private val refillJerry: Boolean by BooleanSetting("Refill Jerry", true, description = "Refill inflatable jerrys.")
    private val refillOnTimer: Boolean by BooleanSetting("Refill on Timer", true, description = "Refill on a 5s intervals.")

    init {
        execute(5000) {
            if (refillOnTimer) refill()
        }

        onMessage(Regex("\\[NPC] Mort: Here, I found this map when I first entered the dungeon\\.")) {
            if (refillOnDungeonStart) refill()
        }
    }

    private fun refill() {
        if (DungeonUtils.isGhost || mc.currentScreen != null || !(inKuudra && KuudraUtils.inKuudra) && !(inDungeon && DungeonUtils.inDungeons)) return
        val inventory = mc.thePlayer?.inventory?.mainInventory ?: return

        inventory.find { it?.itemID == "ENDER_PEARL" }?.takeIf { refillPearl }?.also { fillItemFromSack(16, "ENDER_PEARL", "ender_pearl", false) }

        inventory.find { it?.itemID == "INFLATABLE_JERRY" }?.takeIf { refillJerry }?.also { fillItemFromSack(64, "INFLATABLE_JERRY", "inflatable_jerry", false) }
    }
}