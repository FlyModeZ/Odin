package com.github.stivais.ui.renderer.impl

import com.github.stivais.ui.color.alpha
import com.github.stivais.ui.color.blue
import com.github.stivais.ui.color.green
import com.github.stivais.ui.color.red
import com.github.stivais.ui.renderer.*
import me.odin.lwjgl.Lwjgl3Wrapper
import me.odin.lwjgl.NanoVGColorWrapper
import me.odin.lwjgl.NanoVGGLUFramebufferWrapper
import me.odin.lwjgl.NanoVGPaintWrapper
import me.odinmain.OdinMain.mc
import org.lwjgl.opengl.GL11.*
import java.util.*

class NVGRenderer(private val wrapper: Lwjgl3Wrapper) : Renderer, Lwjgl3Wrapper by wrapper {
    private val nvgPaint: NanoVGPaintWrapper = createPaint()
    private val nvgColor: NanoVGColorWrapper = createColor()
    private val nvgColor2: NanoVGColorWrapper = createColor()

    private val fonts = HashMap<Font, Int>()

    // implement image removing with reference counting
    private val images = HashMap<Image, NVGImage>()

    // not needed afaik
    // private val svgs = HashMap<Image, NVGSvg>()


    private val fbos = HashMap<Framebuffer, NanoVGGLUFramebufferWrapper>()
    private var vg: Long = -1

    // used in getTextWidth to avoid reallocating
    private val fontBounds = FloatArray(4)

    private val scissorStack = Stack<Scissor>()

    private var drawing: Boolean = false

    init {
        vg = nvgCreate(Lwjgl3Wrapper.NVG_ANTIALIAS)
        require(vg != -1L) { "Failed to initialize NanoVG" }
	    check(vg != 0L) { "NanoVG nvgCreate null" }
    }

    override fun beginFrame(width: Float, height: Float) {
        if (drawing) throw IllegalStateException("[NVGRenderer] Already drawing, but called beginFrame")
        drawing = true
        if (!mc.framebuffer.isStencilEnabled) mc.framebuffer.enableStencil()
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        nvgBeginFrame(vg, width, height, 1f)
        nvgTextAlign(vg, Lwjgl3Wrapper.NVG_ALIGN_LEFT or Lwjgl3Wrapper.NVG_ALIGN_TOP)
    }

    override fun endFrame() {
        if (!drawing) throw IllegalStateException("[NVGRenderer] Not drawing, but called endFrame")
        nvgEndFrame(vg)
        glPopAttrib()
        drawing = false
    }

    override fun supportsFramebuffers(): Boolean = false // temp

    override fun createFramebuffer(w: Float, h: Float): Framebuffer {
        val fbo = Framebuffer(w, h)
        fbos[fbo] = nvgluCreateFramebuffer(
            vg,
            w.toInt(),
            h.toInt(),
            0
        ) ?: throw NullPointerException("Error creating nvg fbo")
        println("FBO size: ${fbos.size}")
        return fbo
    }

    override fun drawFramebuffer(fbo: Framebuffer, x: Float, y: Float) {
        val nvgFbo = getFramebuffer(fbo)
        nvgImagePattern(vg, 0f, 0f, fbo.width, fbo.height, 0f, nvgFbo.image(), 1f, nvgPaint)
        nvgBeginPath(vg)
        nvgRect(vg, x, y, fbo.width, fbo.height)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
        nvgClosePath(vg)
    }

    override fun bindFramebuffer(fbo: Framebuffer) {
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        val nvgFbo = getFramebuffer(fbo)
        nvgluBindFramebuffer(vg, nvgFbo)
        glViewport(0, 0, fbo.width.toInt(), fbo.height.toInt())
        glClearColor(0f, 0f,0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
    }

    override fun unbindFramebuffer() {
        nvgluBindFramebuffer(vg, null)
        glPopAttrib()
    }

    private fun getFramebuffer(fbo: Framebuffer): NanoVGGLUFramebufferWrapper {
        return fbos[fbo] ?: throw NullPointerException("Unable to find $fbo")
    }

    override fun destroyFramebuffer(fbo: Framebuffer) {
        val nvgFbo = fbos[fbo] ?: return
        nvgluDeleteFramebuffer(vg, nvgFbo)
        fbos.remove(fbo)
    }

    override fun push() = nvgSave(vg)

    override fun pop() = nvgRestore(vg)

    override fun scale(x: Float, y: Float) = nvgScale(vg, x, y)

    override fun translate(x: Float, y: Float) = nvgTranslate(vg, x, y)

    override fun rotate(amount: Float) = nvgRotate(vg, amount)

    override fun globalAlpha(amount: Float) = nvgGlobalAlpha(vg, amount.coerceIn(0f, 1f))

    override fun pushScissor(x: Float, y: Float, w: Float, h: Float) = nvgScissor(vg, x, y, w, h)

    override fun popScissor() = nvgResetScissor(vg)

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Int) {
        nvgBeginPath(vg)
        nvgMoveTo(vg, x1, y1)
        nvgLineTo(vg, x2, y2)
        nvgStrokeWidth(vg, thickness)
        color(color)
        nvgStrokeColor(vg, nvgColor)
        nvgStroke(vg)
    }

    override fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        nvgBeginPath(vg)
        nvgRect(vg, x, y, w, h + .5f)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    override fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, tl: Float, bl: Float, br: Float, tr: Float) {
        nvgBeginPath(vg)
        nvgRoundedRectVarying(vg, x, y, w, h + .5f, tl, tr, br, bl)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    override fun hollowRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        thickness: Float,
        color: Int,
        tl: Float,
        bl: Float,
        br: Float,
        tr: Float
    ) {
        nvgBeginPath(vg)
        nvgRoundedRectVarying(vg, x, y, w, h, tl, tr, br, bl)
        nvgStrokeWidth(vg, thickness)
        nvgPathWinding(vg, Lwjgl3Wrapper.NVG_HOLE)
        color(color)
        nvgStrokeColor(vg, nvgColor)
        nvgStroke(vg)
    }

    override fun gradientRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color1: Int,
        color2: Int,
        direction: Gradient
    ) {
        nvgBeginPath(vg)
        nvgRect(vg, x, y, w, h)
        gradient(color1, color2, x, y, w, h, direction)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    override fun gradientRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color1: Int,
        color2: Int,
        gradient: Gradient,
        tl: Float,
        bl: Float,
        br: Float,
        tr: Float
    ) {
        nvgBeginPath(vg)
        nvgRoundedRectVarying(vg, x, y, w, h, tl, tr, br, bl)
        gradient(color1, color2, x, y, w, h, gradient)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    override fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Font) {
        nvgBeginPath(vg)
        nvgFontSize(vg, size)
        nvgFontFaceId(vg, getIDFromFont(font))
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgText(vg, x, y, text)
        nvgClosePath(vg)
    }

    override fun textWidth(text: String, size: Float, font: Font): Float {
        nvgFontSize(vg, size)
        nvgFontFaceId(vg, getIDFromFont(font))
        return nvgTextBounds(vg, 0f, 0f, text, fontBounds)
    }

    override fun image(
        image: Image,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        tl: Float,
        bl: Float,
        br: Float,
        tr: Float
    ) {
        val nvgImage = getImage(image) ?: return
        nvgImagePattern(vg, x, y, w, h, 0f, nvgImage, 1f, nvgPaint)
        nvgBeginPath(vg)
        nvgRoundedRectVarying(vg, x, y, w, h, tl, tr, br, bl)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    override fun createImage(image: Image) {
        when (image.type) {
            Image.Type.RASTER -> {
                images.getOrPut(image) { NVGImage(0, loadImage(image)) }.count++
            }
            Image.Type.VECTOR -> {
                images.getOrPut(image) { NVGImage(0, loadSVG(image)) }.count++
            }
        }
    }

    // lowers reference count by 1, if it reaches 0 it gets deleted from mem
    override fun deleteImage(image: Image) {
        val nvgImage = images[image] ?: return
        nvgImage.count--
        if (nvgImage.count == 0) {
            nvgDeleteImage(vg, nvgImage.nvg)
            images.remove(image)
        }
    }

    private fun getImage(image: Image): Int? {
        return images[image]?.nvg//throw IllegalStateException("Image (${image.resourcePath}) doesn't exist")
    }

    private fun loadImage(image: Image): Int {
        val w = IntArray(1)
        val h = IntArray(1)
        val channels = IntArray(1)
        val buffer = stbi_load_from_memory(
            image.buffer(),
            w,
            h,
            channels,
            4
        ) ?: throw NullPointerException("Failed to load image: ${image.resourcePath}")
        return nvgCreateImageRGBA(vg, w[0], h[0], 0, buffer)
    }

    private fun loadSVG(image: Image): Int {
        val vec = image.stream.bufferedReader().readText()
        val svg = nsvgParse(vec, "px", 96f) ?: throw IllegalStateException("Failed to parse ${image.resourcePath}")

        image.width = svg.width()
        image.height = svg.height()

        val memAlloc = memAlloc((svg.width() * svg.height() * 4).toInt())
        val rasterizer = nsvgCreateRasterizer()
        nsvgRasterize(rasterizer, svg, 0f, 0f, 1f, memAlloc, svg.width().toInt(), svg.height().toInt(), svg.width().toInt() * 4)
        val nvgImage = nvgCreateImageRGBA(vg, svg.width().toInt(), svg.height().toInt(), 0, memAlloc)
        nsvgDeleteRasterizer(rasterizer)
        nsvgDelete(svg)
        return nvgImage
    }

    fun color(color: Int) {
        nvgRGBA(color.red.toByte(), color.green.toByte(), color.blue.toByte(), color.alpha.toByte(), nvgColor)
    }

    private fun gradient(color1: Int, color2: Int, x: Float, y: Float, w: Float, h: Float, direction: Gradient) {
        nvgRGBA(color1.red.toByte(), color1.green.toByte(), color1.blue.toByte(), color1.alpha.toByte(), nvgColor)
        nvgRGBA(color2.red.toByte(), color2.green.toByte(), color2.blue.toByte(), color2.alpha.toByte(), nvgColor2)
        when (direction) {
            Gradient.LeftToRight -> nvgLinearGradient(vg, x, y, x + w, y, nvgColor, nvgColor2, nvgPaint)
            Gradient.TopToBottom -> nvgLinearGradient(vg, x, y, x, y + h, nvgColor, nvgColor2, nvgPaint)
        }
    }

    private fun getIDFromFont(font: Font): Int {
        return fonts[font] ?: nvgCreateFontMem(vg, font.name, font.buffer, 0).also {
            fonts[font] = it
        }
    }

    override fun scissor(x: Float, y: Float, width: Float, height: Float): Scissor {
        val scissor = Scissor(x, y, width, height)
        if (scissor in scissorStack) return scissor
        scissorStack.push(scissor)
        applyScissors()
        return scissor
    }

    override fun resetScissor(scissor: Scissor) {
        if (scissorStack.isEmpty()) {
            nvgResetScissor(vg)
        } else {
            if (scissorStack.contains(scissor)) {
                scissorStack.remove(scissor)
                applyScissors()
            }
        }
    }

    override fun clearScissors() {
        scissorStack.clear()
        nvgResetScissor(vg)
    }

    private fun applyScissors() {
        nvgResetScissor(vg)
        if (scissorStack.isEmpty()) return
        if (scissorStack.size == 1) {
            val scissor = scissorStack.peek()
            nvgScissor(vg, scissor.x, scissor.y, scissor.width, scissor.height)
            return
        }
        val finalScissor = getFinalScissor()
        nvgScissor(vg, finalScissor.x, finalScissor.y, finalScissor.width, finalScissor.height)
    }

    private fun getFinalScissor(): Scissor {
        var finalScissor = Scissor(scissorStack[0])
        for (i in 1 until scissorStack.size) {
            val scissor = scissorStack[i]
            val rightX = minOf(scissor.x + scissor.width, finalScissor.x + finalScissor.width)
            val rightY = minOf(scissor.y + scissor.height, finalScissor.y + finalScissor.height)
            finalScissor = Scissor(
                x = maxOf(finalScissor.x, scissor.x),
                y = maxOf(finalScissor.y, scissor.y),
                width = rightX - maxOf(finalScissor.x, scissor.x),
                height = rightY - maxOf(finalScissor.y, scissor.y)
            )
        }
        return finalScissor
    }

    private data class NVGImage(var count: Int, val nvg: Int)
}