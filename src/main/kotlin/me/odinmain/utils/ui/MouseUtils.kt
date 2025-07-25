package me.odinmain.utils.ui

import me.odinmain.OdinMain.mc
import org.lwjgl.input.Mouse

inline val mouseX: Float get() =
    Mouse.getX().toFloat()

inline val mouseY: Float get() =
    mc.displayHeight - Mouse.getY() - 1f

fun isAreaHovered(x: Float, y: Float, w: Float, h: Float): Boolean =
    mouseX in x..x + w && mouseY in y..y + h

fun isAreaHovered(x: Float, y: Float, w: Float): Boolean =
    mouseX in x..x + w && mouseY >= y

fun getQuadrant(): Int =
    when {
        mouseX >= mc.displayWidth / 2 -> if (mouseY >= mc.displayHeight / 2) 4 else 2
        else -> if (mouseY >= mc.displayHeight / 2) 3 else 1
    }