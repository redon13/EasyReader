package io.github.redon13.easyreader.ui.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import org.readium.r2.navigator.OverflowableNavigator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Wraps the reader's pages and, when page curl is on, turns pages like paper.
 *
 * How it works: during a turn this view draws **both** pages itself, from
 * pictures — the page being left and the page being turned to — and covers
 * the real page completely. The real page is only shown again once the turn
 * has fully finished and it is already showing the right page, so nothing
 * underneath can flash through.
 *
 * Pictures are made by drawing the page view into a bitmap (not by copying the
 * screen), so the reader's bars never end up in them. Recent pages are kept,
 * so turning straight back needs no new picture at all.
 *
 * Geometry: the page corner C (bottom-right, or top-right if the touch began
 * in the top half) is pulled to point F. The fold is the line half way
 * between them; everything on C's side is lifted, and its mirror image across
 * the fold is the flap (the back of the page).
 */
class PageCurlLayout(context: Context) : FrameLayout(context) {

    /** Whether page curl is turned on (the Slide / Curl choice in Aa). */
    var curlEnabled = false
        set(value) {
            field = value
            if (value) scheduleCapture()
        }

    /** The reader's page view; set once the book is open. */
    var navigator: OverflowableNavigator? = null

    /** Background colour of the reader theme, used for the back of the page. */
    var paperColor: Int = Color.WHITE
        set(value) {
            field = value
            val dark = isDark(value)
            flapPaint.color = blend(value, if (dark) Color.WHITE else Color.BLACK, if (dark) 0.06f else 0.05f)
            showThroughPaint.alpha = if (dark) 26 else 40
            flapShade.shader = flapGradient(dark)
        }

    // ---- Pictures of pages ---------------------------------------------------------------

    /** Recent page pictures, by page key (most recently used last). */
    private val pictures = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > MAX_PICTURES
    }

    /** Which page came after / before which, learnt from turns, to predict the next picture. */
    private val nextOf = HashMap<String, String>()
    private val prevOf = HashMap<String, String>()

    /** The page showing now (from the reader's location). */
    private var currentKey: String? = null

    /** Called with each new page key while a turn is waiting for the real page to arrive. */
    private var onKey: ((String) -> Unit)? = null

    private val captureRunnable = Runnable { captureCurrentIfMissing() }

    /** Call each time the reader reports a new location. [key] identifies the page. */
    fun onPageShown(key: String) {
        currentKey = key
        onKey?.invoke(key)
        if (state == State.IDLE) scheduleCapture()
    }

    /** Call when the look of pages changes (text size, typeface, background…): old pictures no longer match. */
    fun clearPictures() {
        pictures.clear()
        nextOf.clear()
        prevOf.clear()
        scheduleCapture()
    }

    private fun scheduleCapture() {
        removeCallbacks(captureRunnable)
        if (curlEnabled) postDelayed(captureRunnable, SETTLE_MS)
    }

    private fun captureCurrentIfMissing() {
        val key = currentKey ?: return
        if (state != State.IDLE || !curlEnabled || pictures.containsKey(key)) return
        capture()?.let { pictures[key] = it }
    }

    /** Draws the page view (not the bars above it) into a new bitmap. */
    private fun capture(): Bitmap? {
        val child = getChildAt(0) ?: return null
        if (width == 0 || height == 0) return null
        return try {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(paperColor)
            child.draw(canvas)
            bmp
        } catch (e: Throwable) {
            null
        }
    }

    // ---- Turn state ----------------------------------------------------------------------

    private enum class State { IDLE, DRAGGING, ANIMATING, SETTLING }

    private var state = State.IDLE
    private var forward = true
    private var fromKey: String? = null
    /** The page being left. */
    private var pageA: Bitmap? = null
    /** The page being turned to (null until its picture is ready). */
    private var pageT: Bitmap? = null
    private var targetReady = false
    private var hideWhenReady = false

    private var cornerY = 0f
    private var downX = 0f
    private var downY = 0f
    /** Where the fold is across the page: the page edge plus however far the finger has moved. */
    private var foldX = 0f
    private var fingerY = 0f
    private var velocity: VelocityTracker? = null
    private var animator: ValueAnimator? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    /** True while a turn is under way (taps on the page are ignored then). */
    val isTurning: Boolean get() = state != State.IDLE

    /** Turns a page with a curl, as if the page edge had been tapped. */
    fun animateTurn(forward: Boolean) {
        if (state != State.IDLE || width == 0) return
        downY = height * 0.82f
        downX = if (forward) width.toFloat() else 0f
        beginTurn(forward, downX, downY, auto = true)
    }

    // ---- Touch ---------------------------------------------------------------------------

    private enum class Gesture { NONE, UNDECIDED, PASS_THROUGH, CURL }

    private var gesture = Gesture.NONE

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!curlEnabled || navigator == null) return super.dispatchTouchEvent(ev)
        if (state == State.ANIMATING || state == State.SETTLING) return true

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                gesture = Gesture.UNDECIDED
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
                return super.dispatchTouchEvent(ev) // the page still sees taps
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                when (gesture) {
                    Gesture.CURL -> {
                        foldX = foldFor(ev.x)
                        fingerY = ev.y
                        invalidate()
                        return true
                    }
                    Gesture.PASS_THROUGH -> return super.dispatchTouchEvent(ev)
                    else -> {
                        val dx = ev.x - downX
                        val dy = ev.y - downY
                        if (ev.pointerCount == 1 && abs(dx) > slop && abs(dx) > abs(dy)) {
                            // A sideways drag: the curl takes it. The page never starts its own slide.
                            cancelChildren(ev)
                            gesture = Gesture.CURL
                            beginTurn(forward = dx < 0, x = ev.x, y = ev.y, auto = false)
                            return true
                        }
                        if (abs(dy) > slop || ev.pointerCount > 1) {
                            // Up/down or two fingers: let the page handle the rest of it.
                            gesture = Gesture.PASS_THROUGH
                            return super.dispatchTouchEvent(ev)
                        }
                        // Still deciding: keep small movements away from the page.
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val g = gesture
                gesture = Gesture.NONE
                if (g == Gesture.CURL) {
                    velocity?.addMovement(ev)
                    velocity?.computeCurrentVelocity(1000)
                    val vx = velocity?.xVelocity ?: 0f
                    foldX = foldFor(ev.x)
                    fingerY = ev.y
                    val fast = abs(vx) > FLING_SPEED
                    val complete = when {
                        ev.actionMasked == MotionEvent.ACTION_CANCEL -> false
                        forward -> (fast && vx < 0) || (!fast && foldX < width / 2f)
                        else -> (fast && vx > 0) || (!fast && foldX > width / 2f)
                    }
                    if (state == State.DRAGGING) animateTo(complete, vx)
                    return true
                }
                return super.dispatchTouchEvent(ev)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun cancelChildren(ev: MotionEvent) {
        val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    /** Fold position for a finger at [x]: starts at the right edge (forward) or left edge (back). */
    private fun foldFor(x: Float) = (if (forward) width.toFloat() else 0f) + (x - downX)

    // ---- Turning ---------------------------------------------------------------------------

    private fun beginTurn(forward: Boolean, x: Float, y: Float, auto: Boolean) {
        val nav = navigator ?: return
        this.forward = forward
        cornerY = if (downY > height / 2f) height.toFloat() else 0f
        foldX = foldFor(x)
        fingerY = y
        hideWhenReady = false
        removeCallbacks(captureRunnable)

        // The page being left: usually pictured already; otherwise draw it now, before moving.
        val key = currentKey
        val a = key?.let { pictures[it] } ?: capture()
        if (a == null) {
            // Can't picture the page: fall back to an ordinary page turn.
            if (forward) nav.goForward(animated = true) else nav.goBackward(animated = true)
            return
        }
        if (key != null) pictures[key] = a
        fromKey = key
        pageA = a

        // The page being turned to: known already if we've just come from there.
        val predictedKey = key?.let { if (forward) nextOf[it] else prevOf[it] }
        pageT = predictedKey?.let { pictures[it] }
        targetReady = false

        state = if (auto) State.ANIMATING else State.DRAGGING
        invalidate()

        // Once the picture of the page being left is on screen, move the real page underneath.
        postOnAnimation {
            val moved = if (forward) nav.goForward(animated = false) else nav.goBackward(animated = false)
            if (!moved) {
                // Start or end of the book.
                animator?.cancel()
                hide()
                return@postOnAnimation
            }
            waitForPage({ it != fromKey }) { newKey ->
                if (newKey != null) {
                    if (pageT == null || newKey != predictedKey) {
                        pageT = capture()
                    }
                    pageT?.let { pictures[newKey] = it }
                    val from = fromKey
                    if (from != null) {
                        if (forward) {
                            nextOf[from] = newKey
                            prevOf[newKey] = from
                        } else {
                            prevOf[from] = newKey
                            nextOf[newKey] = from
                        }
                    }
                } else if (pageT == null) {
                    pageT = capture()
                }
                targetReady = true
                invalidate()
                if (hideWhenReady) hide()
            }
            if (auto) animateTo(complete = true, velocity = 0f)
        }
    }

    /**
     * Waits until the reader reports a page matching [accept] (or a short time
     * has passed), then for two more frames so it's drawn, then calls [done]
     * with the page key (null if it timed out).
     */
    private fun waitForPage(accept: (String) -> Boolean, done: (String?) -> Unit) {
        var finished = false
        val timeout = Runnable {
            if (finished) return@Runnable
            finished = true
            onKey = null
            afterFrames(2) { done(currentKey?.takeIf(accept)) }
        }
        onKey = { key ->
            if (!finished && accept(key)) {
                finished = true
                onKey = null
                removeCallbacks(timeout)
                afterFrames(2) { done(key) }
            }
        }
        postDelayed(timeout, PAGE_TIMEOUT_MS)
    }

    private fun afterFrames(n: Int, action: () -> Unit) {
        if (n <= 0) action() else postOnAnimation { afterFrames(n - 1, action) }
    }

    private fun animateTo(complete: Boolean, velocity: Float) {
        state = State.ANIMATING
        val startX = foldX
        val startY = fingerY
        // Turned over: a little beyond the left edge, so the flap and its shadow leave cleanly.
        // Lying flat again: exactly at the right edge.
        val endX = if (forward == complete) -width * 0.2f else width.toFloat()
        val endY = downY
        val speed = abs(velocity).coerceAtLeast(MIN_FINISH_SPEED)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (abs(endX - startX) / speed * 1000 * 1.4f).toLong().coerceIn(160L, 340L)
            interpolator = DecelerateInterpolator(1.0f)
            addUpdateListener {
                val t = it.animatedValue as Float
                foldX = startX + (endX - startX) * t
                fingerY = startY + (endY - startY) * t
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (complete) {
                        // The real page is already on the new page: show it once it's been drawn.
                        if (targetReady) {
                            hide()
                        } else {
                            state = State.SETTLING
                            hideWhenReady = true
                            invalidate()
                        }
                    } else {
                        // Cancelled: keep showing the page we started on while the real page
                        // goes back to it, then show the real page.
                        state = State.SETTLING
                        hideWhenReady = false
                        invalidate()
                        val nav = navigator
                        val moved = if (forward) nav?.goBackward(animated = false) else nav?.goForward(animated = false)
                        if (moved == true) waitForPage({ it == fromKey || fromKey == null }) { hide() } else hide()
                    }
                }
            })
            start()
        }
    }

    private fun hide() {
        state = State.IDLE
        hideWhenReady = false
        onKey = null
        pageA = null
        pageT = null
        invalidate()
        scheduleCapture()
    }

    // ---- Drawing ---------------------------------------------------------------------------

    // Reused on every frame, so drawing doesn't create new objects 60 times a second.
    private val flatPath = Path()
    private val liftedPath = Path()
    private val flapPath = Path()
    private val shaderMatrix = Matrix()
    private val mirror = Matrix()

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paperPaint = Paint()
    private val flapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val showThroughPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Soft shadow cast by the flap onto the page beneath (unit gradient placed with a matrix). */
    private val foldShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 1f, 0f, Color.argb(80, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }

    /** Light and shade across the flap, like a gently rolled sheet of paper. */
    private val flapShade = Paint(Paint.ANTI_ALIAS_FLAG)

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(26, 0, 0, 0)
    }

    init {
        setWillNotDraw(false)
        paperColor = Color.WHITE
    }

    private fun flapGradient(dark: Boolean) = LinearGradient(
        0f, 0f, 1f, 0f,
        if (dark) {
            intArrayOf(Color.argb(60, 0, 0, 0), Color.argb(16, 255, 255, 255), Color.argb(10, 0, 0, 0), Color.TRANSPARENT)
        } else {
            intArrayOf(Color.argb(55, 0, 0, 0), Color.argb(40, 255, 255, 255), Color.argb(16, 0, 0, 0), Color.TRANSPARENT)
        },
        floatArrayOf(0f, 0.22f, 0.6f, 1f),
        Shader.TileMode.CLAMP,
    )

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        when (state) {
            State.IDLE -> return
            State.SETTLING -> {
                // Waiting for the real page: show the page that will be there.
                drawPage(canvas, if (hideWhenReady) pageT else pageA)
            }
            else -> drawCurl(canvas)
        }
    }

    /** A whole page from its picture, or plain paper if the picture isn't ready yet. */
    private fun drawPage(canvas: Canvas, bmp: Bitmap?) {
        if (bmp != null) {
            canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        } else {
            paperPaint.color = paperColor
            canvas.drawPaint(paperPaint)
        }
    }

    private fun drawCurl(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Turning forward, the page being left lifts to reveal the next one.
        // Turning back, the previous page comes down over the page being left.
        val turning = if (forward) pageA else pageT
        val beneath = if (forward) pageT else pageA

        // Corner C is pulled to F. The fold sits half way between them, so it follows the finger.
        // The fold never goes past the right edge: beyond it the turning page is simply lying
        // flat. (Letting it go further made the whole page count as lifted for a moment, which
        // flashed the wrong page at the end of a turn back.)
        val fold = foldX.coerceAtMost(w)
        val cx = w
        val cy = cornerY
        val fx = 2 * fold - w
        val fy = (cy + (fingerY - downY) * 0.6f).coerceIn(cy - h * 0.6f, cy + h * 0.6f)
        val dist = hypot(fx - cx, fy - cy)

        if (dist < 1f) {
            // Not lifted at all: just the turning page.
            drawPage(canvas, turning)
            return
        }
        val dx = (fx - cx) / dist
        val dy = (fy - cy) / dist
        val mx = (cx + fx) / 2
        val my = (cy + fy) / 2
        fun side(p: PointF) = (p.x - mx) * dx + (p.y - my) * dy

        val page = listOf(PointF(0f, 0f), PointF(w, 0f), PointF(w, h), PointF(0f, h))
        val flat = clip(page, keepPositive = true, side = ::side)   // still lying flat
        val lifted = clip(page, keepPositive = false, side = ::side) // lifted off the page
        val flap = lifted.map { p ->
            val d = side(p)
            PointF(p.x - 2 * d * dx, p.y - 2 * d * dy)
        }
        toPath(flat, flatPath)
        toPath(lifted, liftedPath)
        toPath(flap, flapPath)

        // The part of the turning page still lying flat.
        if (flat.size >= 3) {
            canvas.save()
            canvas.clipPath(flatPath)
            drawPage(canvas, turning)
            canvas.restore()
        }

        // The page beneath, where the turning page has lifted away, with the flap's soft shadow.
        val soft = (dist * 0.22f).coerceIn(8f, w * 0.1f)
        if (lifted.size >= 3) {
            canvas.save()
            canvas.clipPath(liftedPath)
            drawPage(canvas, beneath)
            placeShader(foldShadow, mx, my, -dx, -dy, soft)
            canvas.drawPaint(foldShadow)
            canvas.restore()
        }

        // The flap: the back of the turning page.
        if (flap.size >= 3) {
            canvas.drawPath(flapPath, flapPaint)
            canvas.save()
            canvas.clipPath(flapPath)
            // Show-through: the page's own text, mirrored and faint, as if seen through thin paper.
            // The mirror is a reflection across the fold line (normal d, through M).
            if (turning != null) {
                val k = 2 * (mx * dx + my * dy)
                mirror.setValues(
                    floatArrayOf(
                        1 - 2 * dx * dx, -2 * dx * dy, k * dx,
                        -2 * dx * dy, 1 - 2 * dy * dy, k * dy,
                        0f, 0f, 1f,
                    ),
                )
                canvas.drawBitmap(turning, mirror, showThroughPaint)
            }
            // Light and shade across the flap so it looks rolled, not folded flat.
            placeShader(flapShade, mx, my, dx, dy, (dist / 2).coerceAtLeast(soft * 2))
            canvas.drawPaint(flapShade)
            canvas.restore()
            canvas.drawPath(flapPath, edgePaint)
        }
    }

    /** Points a unit-length gradient from (x, y) in direction (ux, uy), stretched to [length]. */
    private fun placeShader(paint: Paint, x: Float, y: Float, ux: Float, uy: Float, length: Float) {
        shaderMatrix.reset()
        shaderMatrix.setScale(length, length)
        shaderMatrix.postRotate(Math.toDegrees(atan2(uy, ux).toDouble()).toFloat())
        shaderMatrix.postTranslate(x, y)
        paint.shader?.setLocalMatrix(shaderMatrix)
    }

    /** Cuts a polygon by the fold line, keeping one side (Sutherland–Hodgman, one edge). */
    private fun clip(poly: List<PointF>, keepPositive: Boolean, side: (PointF) -> Float): List<PointF> {
        val out = ArrayList<PointF>(6)
        fun inside(p: PointF) = if (keepPositive) side(p) >= 0 else side(p) < 0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val ina = inside(a)
            val inb = inside(b)
            if (ina) out += a
            if (ina != inb) {
                val sa = side(a)
                val sb = side(b)
                val t = sa / (sa - sb)
                out += PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        return out
    }

    private fun toPath(poly: List<PointF>, target: Path) {
        target.reset()
        if (poly.isEmpty()) return
        target.moveTo(poly[0].x, poly[0].y)
        for (i in 1 until poly.size) target.lineTo(poly[i].x, poly[i].y)
        target.close()
    }

    private fun isDark(color: Int) =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000 < 128

    private fun blend(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private companion object {
        /** How many recent page pictures to keep (about 9 MB each on the phone). */
        const val MAX_PICTURES = 4

        /** Wait this long after a page appears before picturing it. */
        const val SETTLE_MS = 150L

        /** Longest wait for the reader to arrive at a page before carrying on anyway. */
        const val PAGE_TIMEOUT_MS = 600L

        /** A release faster than this (pixels per second) counts as a flick. */
        const val FLING_SPEED = 1000f

        /** Slowest speed (pixels per second) for finishing a turn after letting go. */
        const val MIN_FINISH_SPEED = 2600f
    }
}
