package io.github.redon13.easyreader.ui.reader

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
import android.graphics.Rect
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import org.readium.r2.navigator.OverflowableNavigator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Wraps the reader's pages and, when page curl is on, turns pages like paper.
 *
 * How it works: just before a turn, a picture of the current page is taken.
 * The reader then moves to the next (or previous) page underneath, and this
 * view draws the picture folding over along a line that follows the finger,
 * with a paper-coloured flap (showing the page's text faintly, mirrored, as
 * if through thin paper) and soft shadows. When the turn finishes the
 * picture is dropped and the real page is shown again.
 *
 * Geometry: the page corner C (bottom-right, or top-right if the touch began
 * in the top half) is pulled to point F. The fold is the line half way
 * between them; everything on C's side is lifted, and its mirror image across
 * the fold is the flap (the back of the page).
 */
class PageCurlLayout(context: Context, private val window: Window) : FrameLayout(context) {

    /** Whether page curl is turned on (the Slide / Curl choice in Aa). */
    var curlEnabled = false
        set(value) {
            field = value
            pageChanged()
        }

    /** The reader's page view; set once the book is open. */
    var navigator: OverflowableNavigator? = null

    /** Background colour of the reader theme, used for the back of the page. */
    var paperColor: Int = Color.WHITE
        set(value) {
            field = value
            flapPaint.color = blend(value, if (isDark(value)) Color.WHITE else Color.BLACK, 0.07f)
        }

    private enum class State { IDLE, PREPARING, DRAGGING, ANIMATING, SETTLING }

    private var state = State.IDLE
    private var picture: Bitmap? = null
    private var forward = true
    private var cornerY = 0f
    private var downX = 0f
    private var downY = 0f
    /** Where the fold is across the page: the page edge plus however far the finger has moved. */
    private var foldX = 0f
    private var fingerY = 0f
    private var velocity: VelocityTracker? = null
    private var animator: ValueAnimator? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 1.5f

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val flapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mirror = Matrix()
    private val showThroughPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 46 }

    init {
        setWillNotDraw(false)
        paperColor = Color.WHITE
    }

    /** True when [picture] shows the page exactly as it is on screen now. */
    private var pictureFresh = false
    private val refreshRunnable = Runnable { refreshPicture() }

    /**
     * Call whenever what's on screen changes (new page, bars shown or hidden,
     * reading settings). A fresh picture is taken once the page has settled,
     * so the next turn can start instantly instead of waiting for one.
     */
    fun pageChanged() {
        pictureFresh = false
        removeCallbacks(refreshRunnable)
        if (curlEnabled) postDelayed(refreshRunnable, SETTLE_MS)
    }

    private fun refreshPicture() {
        if (state != State.IDLE || !curlEnabled) return
        takePicture { bmp ->
            if (bmp != null && state == State.IDLE) {
                picture = bmp
                pictureFresh = true
            }
        }
    }

    /** True while a turn is under way (taps on the page are ignored then). */
    val isTurning: Boolean get() = state != State.IDLE

    /** Turns a page with a curl, as if the page edge had been tapped. */
    fun animateTurn(forward: Boolean) {
        if (state != State.IDLE || width == 0) return
        val y = height * 0.82f
        downY = y
        downX = if (forward) width.toFloat() else 0f
        beginTurn(forward, downX, y) {
            animateTo(complete = true, velocity = 0f)
        }
    }

    // ---- Touch ----------------------------------------------------------------

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!curlEnabled || navigator == null) return super.dispatchTouchEvent(ev)
        if (state == State.ANIMATING || state == State.SETTLING) return true
        if (state == State.PREPARING || state == State.DRAGGING) {
            handleDrag(ev)
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (ev.pointerCount == 1 && abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                    // A sideways drag: take it over from the page view and start a curl.
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    beginTurn(forward = dx < 0, x = ev.x, y = ev.y, then = null)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun handleDrag(ev: MotionEvent) {
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                foldX = foldFor(ev.x)
                fingerY = ev.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                foldX = foldFor(ev.x)
                fingerY = ev.y
                velocity?.computeCurrentVelocity(1000)
                val vx = velocity?.xVelocity ?: 0f
                val fast = abs(vx) > 1200f
                val complete = when {
                    ev.actionMasked == MotionEvent.ACTION_CANCEL -> false
                    forward -> (fast && vx < 0) || (!fast && foldX < width / 2f)
                    else -> (fast && vx > 0) || (!fast && foldX > width / 2f)
                }
                releaseVelocity = vx
                if (state == State.PREPARING) {
                    // The picture isn't ready yet: finish as soon as it is.
                    pendingRelease = complete
                } else {
                    animateTo(complete, vx)
                }
            }
        }
    }

    private var pendingRelease: Boolean? = null
    private var releaseVelocity = 0f

    /** Fold position for a finger at [x]: starts at the right edge (forward) or left edge (back). */
    private fun foldFor(x: Float) = (if (forward) width.toFloat() else 0f) + (x - downX)

    // ---- Turning ------------------------------------------------------------------

    private fun beginTurn(forward: Boolean, x: Float, y: Float, then: (() -> Unit)?) {
        state = State.PREPARING
        this.forward = forward
        cornerY = if (downY > height / 2f) height.toFloat() else 0f
        foldX = foldFor(x)
        fingerY = y
        pendingRelease = null
        removeCallbacks(refreshRunnable)

        fun start(bmp: Bitmap) {
            val nav = navigator ?: return finish()
            picture = bmp
            pictureFresh = false
            state = State.DRAGGING
            invalidate()
            // Move the real page underneath once the picture is on screen.
            postOnAnimation {
                val moved = if (forward) nav.goForward(animated = false) else nav.goBackward(animated = false)
                if (!moved) {
                    // Start or end of the book.
                    animator?.cancel()
                    finish()
                    return@postOnAnimation
                }
                then?.invoke()
                pendingRelease?.let { animateTo(it, releaseVelocity) }
            }
        }

        val ready = picture?.takeIf { pictureFresh && it.width == width && it.height == height && !it.isRecycled }
        if (ready != null) {
            // Taken in advance: the curl starts straight away.
            start(ready)
            return
        }
        takePicture { bmp ->
            if (bmp == null || navigator == null) {
                // No picture: fall back to an ordinary page turn.
                state = State.IDLE
                if (forward) navigator?.goForward(animated = true) else navigator?.goBackward(animated = true)
                return@takePicture
            }
            start(bmp)
        }
    }

    private fun animateTo(complete: Boolean, velocity: Float) {
        if (picture == null) return
        state = State.ANIMATING
        val startX = foldX
        val startY = fingerY
        // The fold follows the finger: fully over to the left, or back to the right.
        val endX = if (forward == complete) -width * 0.05f else width * 1.05f
        val endY = downY
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            // Carry on at about the speed of the flick (or a calm pace after a tap or slow drag),
            // easing out gently at the end.
            val speed = abs(velocity).coerceAtLeast(MIN_FINISH_SPEED)
            duration = (abs(endX - startX) / speed * 1000 * 1.6f).toLong().coerceIn(180L, 420L)
            interpolator = DecelerateInterpolator(1.3f)
            addUpdateListener {
                val t = it.animatedValue as Float
                foldX = startX + (endX - startX) * t
                fingerY = startY + (endY - startY) * t
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (complete) {
                        finish()
                    } else {
                        // Cancelled: go back to the page we started on, keeping the picture
                        // on screen until the real page has been drawn again.
                        state = State.SETTLING
                        foldX = if (forward) width.toFloat() else 0f
                        invalidate()
                        if (forward) navigator?.goBackward(animated = false) else navigator?.goForward(animated = false)
                        postDelayed({ finish() }, 180)
                    }
                }
            })
            start()
        }
    }

    private fun finish() {
        state = State.IDLE
        pendingRelease = null
        invalidate()
        pageChanged()
    }

    private fun takePicture(done: (Bitmap?) -> Unit) {
        if (width == 0 || height == 0) return done(null)
        val bmp = picture?.takeIf { it.width == width && it.height == height && !it.isRecycled }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val loc = IntArray(2)
        getLocationInWindow(loc)
        try {
            PixelCopy.request(
                window,
                Rect(loc[0], loc[1], loc[0] + width, loc[1] + height),
                bmp,
                { result -> done(if (result == PixelCopy.SUCCESS) bmp else null) },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: IllegalArgumentException) {
            done(null)
        }
    }

    // ---- Drawing -------------------------------------------------------------------

    // Reused on every frame, so drawing doesn't create new objects 60 times a second.
    private val flatPath = Path()
    private val liftedPath = Path()
    private val flapPath = Path()
    private val shaderMatrix = Matrix()

    /** Soft shadow fading away from the fold (drawn along a unit line, then placed with a matrix). */
    private val foldShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 1f, 0f, Color.argb(95, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }

    /** Light and shade across the flap, like a gently rolled sheet of paper. */
    private val flapShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 1f, 0f,
            intArrayOf(
                Color.argb(70, 0, 0, 0),
                Color.argb(45, 255, 255, 255),
                Color.argb(22, 0, 0, 0),
                Color.argb(0, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.2f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.argb(30, 0, 0, 0)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val bmp = picture ?: return
        when (state) {
            State.IDLE, State.PREPARING -> return
            State.SETTLING -> canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
            else -> drawCurl(canvas, bmp)
        }
    }

    private fun drawCurl(canvas: Canvas, bmp: Bitmap) {
        val w = width.toFloat()
        val h = height.toFloat()
        val page = listOf(PointF(0f, 0f), PointF(w, 0f), PointF(w, h), PointF(0f, h))

        // Corner C is pulled to F. The fold sits half way between them, so it follows the finger.
        val cx = w
        val cy = cornerY
        val fx = 2 * foldX - w
        val fy = (cy + (fingerY - downY) * 0.6f).coerceIn(cy - h * 0.6f, cy + h * 0.6f)
        val dist = hypot(fx - cx, fy - cy)

        if (dist < 1f) {
            // Not lifted at all.
            if (forward) canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
            return
        }
        val dx = (fx - cx) / dist
        val dy = (fy - cy) / dist
        val mx = (cx + fx) / 2
        val my = (cy + fy) / 2
        fun side(p: PointF) = (p.x - mx) * dx + (p.y - my) * dy

        val flat = clip(page, keepPositive = true, side = ::side)   // still lying flat
        val lifted = clip(page, keepPositive = false, side = ::side) // lifted off the page
        val flap = lifted.map { p ->
            val d = side(p)
            PointF(p.x - 2 * d * dx, p.y - 2 * d * dy)
        }
        toPath(flat, flatPath)
        toPath(lifted, liftedPath)
        toPath(flap, flapPath)

        // Forward: the old page (picture) is the part still flat; the new page shows where it lifted.
        // Backward: the new (previous) page lies flat; the old page (picture) shows where it lifted.
        canvas.save()
        canvas.clipPath(if (forward) flatPath else liftedPath)
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        canvas.restore()

        val depth = dist / 2                                   // how far the flap reaches from the fold
        val soft = (dist * 0.25f).coerceIn(8f, w * 0.12f)      // width of the soft shadows

        // Shadow cast by the flap onto the page beneath, along the fold.
        if (lifted.size >= 3) {
            placeShader(foldShadow, mx, my, -dx, -dy, soft)
            canvas.save()
            canvas.clipPath(liftedPath)
            canvas.drawPaint(foldShadow)
            canvas.restore()
        }

        // The flap: the back of the turning page.
        if (flap.size >= 3) {
            canvas.drawPath(flapPath, flapPaint)
            // Show-through: the page's own text, mirrored and faint, as if seen through thin paper.
            // The mirror is a reflection across the fold line (normal d, through M).
            val k = 2 * (mx * dx + my * dy)
            mirror.setValues(
                floatArrayOf(
                    1 - 2 * dx * dx, -2 * dx * dy, k * dx,
                    -2 * dx * dy, 1 - 2 * dy * dy, k * dy,
                    0f, 0f, 1f,
                ),
            )
            canvas.save()
            canvas.clipPath(flapPath)
            canvas.drawBitmap(bmp, mirror, showThroughPaint)
            // Light and shade across the flap so it looks rolled, not folded flat.
            placeShader(flapShade, mx, my, dx, dy, depth.coerceAtLeast(soft * 2))
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
        paint.shader.setLocalMatrix(shaderMatrix)
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
        /** Wait this long after the page changes before taking the next picture. */
        const val SETTLE_MS = 350L

        /** Slowest speed (pixels per second) for finishing a turn after letting go. */
        const val MIN_FINISH_SPEED = 2200f
    }
}
