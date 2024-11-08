package me.odinmain.features.impl.nether

import com.github.stivais.ui.animation.Animations
import com.github.stivais.ui.constraints.px
import com.github.stivais.ui.transforms.Transforms
import com.github.stivais.ui.utils.seconds
import me.odinmain.events.impl.PacketReceivedEvent
import me.odinmain.events.impl.RealServerTick
import me.odinmain.features.Module
import me.odinmain.features.settings.impl.BooleanSetting
import me.odinmain.features.settings.impl.SelectorSetting
import me.odinmain.utils.skyblock.skyblockID
import me.odinmain.utils.ui.TextHUD
import me.odinmain.utils.ui.and
import net.minecraft.network.play.server.S29PacketSoundEffect
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnrageDisplay : Module(
    name = "Enrage Display",
    description = "Displays the Reaper armor's ability duration."
) {
    private val unit by SelectorSetting("Unit", arrayListOf("Seconds", "Ticks"))
    private val showUnit by BooleanSetting("Show unit", default = false)

    // test
    private val animation = Transforms.Alpha.Animated(from = 0f, to = 1f)

    private val HUD = TextHUD(
        "Enrage Display",
        "Displays the duration on screen."
    ) { color, font ->
        if (!preview) transform(animation)
        text(
            "Enrage ",
            color = color,
            font = font,
            size = 30.px
        ) and text({ getDisplay(if (preview) 120 else enrageTimer) }, font = font)
    }

    private fun getDisplay(ticks: Int): String {
        return when (unit) {
            0 -> "${ticks / 20}${if (showUnit) "s" else ""}"
            else -> "$ticks${if (showUnit) "t" else ""}"
        }
    }

    private var enrageTimer = -1

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        val packet = event.packet as? S29PacketSoundEffect ?: return
        if (packet.soundName == "mob.zombie.remedy" && packet.pitch == 1.0f && packet.volume == 0.5f) {
            if (
                mc.thePlayer?.getCurrentArmor(0)?.skyblockID == "REAPER_BOOTS" &&
                mc.thePlayer?.getCurrentArmor(1)?.skyblockID == "REAPER_LEGGINGS" &&
                mc.thePlayer?.getCurrentArmor(2)?.skyblockID == "REAPER_CHESTPLATE"
            ) {
                enrageTimer = 120
                animation.animate(0.25.seconds, Animations.EaseOutQuint)
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: RealServerTick) {
        enrageTimer--
        if (enrageTimer == 0) {
            animation.animate(0.25.seconds, Animations.EaseOutQuint)
        }
    }
}