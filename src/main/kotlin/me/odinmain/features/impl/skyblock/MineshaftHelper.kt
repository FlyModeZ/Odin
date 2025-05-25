package me.odinmain.features.impl.skyblock

import me.odinmain.features.Module
import me.odinmain.features.settings.impl.BooleanSetting
import me.odinmain.utils.noControlCodes
import me.odinmain.utils.skyblock.LocationUtils.currentArea
import me.odinmain.utils.skyblock.Island
import me.odinmain.utils.skyblock.modMessage
import me.odinmain.events.impl.PacketEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraft.network.play.server.S3EPacketTeams

object MineshaftHelper : Module(
    name = "Mineshaft",
    desc = "Announce party when you enter a mineshaft."
) {
    private val variantRegex = Regex("^\\d\\d/\\d\\d/\\d\\d .+ (\\w{4}\\d)$")
    private val noUse by BooleanSetting("Lapis Count Only", false, desc = "only show lapis cropse count")
    @SubscribeEvent()
    fun onPacket(event: PacketEvent.Receive) {
        when (event.packet) {
            is S3EPacketTeams -> {
                if (!currentArea.isArea(Island.Mineshaft) || event.packet.action != 2) return
                val text = event.packet.prefix.plus(event.packet.suffix).noControlCodes
                modMessage(text)
                variantRegex.find(text)?.groupValues?.get(1)?.let { modMessage(it) }
            }
        }
    }
}
