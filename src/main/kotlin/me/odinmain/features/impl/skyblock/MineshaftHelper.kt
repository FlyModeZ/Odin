package me.odinmain.features.impl.skyblock

import me.odinmain.features.Module
import me.odinmain.features.settings.impl.BooleanSetting

object MineshaftHelper : Module(
    name = "Mineshaft",
    desc = "Announce party when you enter a mineshaft."
) {
	private val noUse by BooleanSetting("Lapis Count Only", false, desc = "only show lapis cropse count")
}
