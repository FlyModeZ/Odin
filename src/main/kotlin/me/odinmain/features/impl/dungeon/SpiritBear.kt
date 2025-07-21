package me.odinmain.features.impl.dungeon

import me.odinmain.events.impl.BlockChangeEvent
import me.odinmain.features.Module
import me.odinmain.features.settings.impl.BooleanSetting
import me.odinmain.features.settings.impl.HudSetting
import me.odinmain.utils.skyblock.dungeon.DungeonUtils
import me.odinmain.utils.render.mcTextAndWidth
import me.odinmain.utils.toFixed
import me.odinmain.utils.ui.Colors
import net.minecraft.init.Blocks
import net.minecraft.network.play.server.S32PacketConfirmTransaction
import net.minecraft.util.BlockPos
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object SpiritBear : Module(
    name = "Spirit Bear",
    desc = "Displays the current state of Spirit Bear."
) {
    private val hud by HudSetting("Hud", 10f, 10f, 1f, true) { example ->
        when {
            example -> "§61.45s"
            !DungeonUtils.isFloor(4) || !DungeonUtils.inBoss -> null
            timer > 0 -> "§6${(timer / 20f).toFixed()}s"
            timer == 0 -> "§aAlive!"
            showNotSpawned -> "§cNot Spawned"
            else -> null
        }?.let { text ->
            mcTextAndWidth("§eSpirit Bear: $text §f[$kills]", 0f, 0f, 1f, Colors.WHITE, center = false) + 2f to 12f
        } ?: (0f to 0f)
    }
    private val showNotSpawned by BooleanSetting("Show Not Spawned", false, desc = "Show the Spirit Bear hud even when it's not spawned.")

    private val lastBlockLocation = BlockPos(7, 77, 34)
    private var timer = -1 // state: -1=NotSpawned, 0=Alive, 1+=Spawning

    private val f4BlockLocation = hashSetOf(BlockPos(-3, 77, 33), BlockPos(-9, 77, 31), BlockPos(-16, 77, 26), BlockPos(-20, 77, 20), BlockPos(-23, 77, 13), BlockPos(-24, 77, 6), BlockPos(-24, 77, 0), BlockPos(-22, 77, -7), BlockPos(-18, 77, -13), BlockPos(-12, 77, -19), BlockPos(-5, 77, -22), BlockPos(1, 77, -24), BlockPos(8, 77, -24), BlockPos(14, 77, -23), BlockPos(21, 77, -19), BlockPos(27, 77, -14), BlockPos(31, 77, -8), BlockPos(33, 77, -1), BlockPos(34, 77, 5), BlockPos(33, 77, 12), BlockPos(31, 77, 19), BlockPos(27, 77, 25), BlockPos(20, 77, 30), BlockPos(14, 77, 33), BlockPos(7, 77, 34),)
    private var kills = 0

    init {
        onPacket<S32PacketConfirmTransaction> { if (timer > 0) timer -- }
        onWorldLoad { timer = -1 }
    }

    @SubscribeEvent
    fun onBlockChange(event: BlockChangeEvent) {
        if (!DungeonUtils.isFloor(4) || !DungeonUtils.inBoss) return
        when {
            f4BlockLocation.contains(event.pos) && event.updated.block == Blocks.sea_lantern -> kills ++
            event.pos != lastBlockLocation -> return
            event.updated.block == Blocks.sea_lantern -> timer = 68 // bear starts to spawn
            event.updated.block == Blocks.coal_block -> { timer = -1; kills = 0} // bear dead
        }
    }
}
