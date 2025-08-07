package net.bunnystream.bunnystreamplayer.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.media3.common.C
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TimeBar
import net.bunny.bunnystreamplayer.model.Chapter
import net.bunny.bunnystreamplayer.model.Moment
import net.bunny.bunnystreamplayer.model.RetentionGraphEntry
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import androidx.core.graphics.createBitmap

@UnstableApi
class BunnyTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), TimeBar {

    companion object {
        private const val DEFAULT_PLAYED_COLOR = 0xFFFFFFFF.toInt()
        private const val DEFAULT_CHAPTER_SELECTED_COLOR = 0xFFFFFFFF.toInt()
        private const val DEFAULT_UNPLAYED_COLOR = 0x88FFFFFF.toInt()
        private const val DEFAULT_BUFFERED_COLOR = 0xDDEEEEEE.toInt()
        private const val DEFAULT_SCRUBBER_COLOR = 0xFFFFFFFF.toInt()
        private const val DEFAULT_MOMENT_COLOR = 0xAAFFFFFF.toInt()
        private const val DEFAULT_GRAPH_OVERLAY_COLOR = Color.WHITE
        private const val DEFAULT_GRAPH_GRADIENT_START_COLOR = 0x88FFFFFF.toInt()
        private const val DEFAULT_GRAPH_GRADIENT_END_COLOR = 0x00000000

        private const val TIME_BAR_HEIGHT_DP = 2
        private const val TIME_BAR_BOTTOM_MARGIN_DP = 10

        private const val TOUCH_TARGET_HEIGHT_DP = 20
        private const val SCRUBBER_ENABLED_SIZE_DP = 12
        private const val SCRUBBER_DRAGGED_SIZE_DP = 24
        private const val SCRUBBER_DISABLED_SIZE_DP = 4
        private const val RETENTION_GRAPH_HEIGHT_DP = 40
        private const val RETENTION_GRAPH_BOTTOM_MARGIN_DP = 5

        private const val GAP_SIZE_DP = 2
        private const val CHAPTER_THICKNESS_DELTA_DP = 2
        private const val ADDITIONAL_DRAG_OFFSET_DP = 4
        private const val INIT_EDGE_DP = 3

        private const val MOMENT_POINT_SIZE = 30
        private const val MOMENT_POSITION_PADDING_MILLIS = 1000
    }

    private val seekBounds = Rect()
    private val progressBar = Rect()
    private val positionBar = Rect()
    private val bufferedBar = Rect()
    private val scrubberBar = Rect()
    private val graphBounds = Rect()
    private val retentionGraphOverlayBounds = Rect()

    private val retentionGraphFillPath = Path()

    private var scrubPosition = 0L
    private var duration = C.TIME_UNSET
    private var position = 0L
    private var bufferedPosition = 0L

    private val playedPaint = paintWithColor(DEFAULT_PLAYED_COLOR)
    private val bufferedPaint = paintWithColor(DEFAULT_BUFFERED_COLOR)
    private val unPlayedPaint = paintWithColor(DEFAULT_UNPLAYED_COLOR)
    private val scrubberCirclePaint = paintWithColor(DEFAULT_SCRUBBER_COLOR)
    private val momentsPaint = paintWithColor(DEFAULT_MOMENT_COLOR)
    private val chapterSelectedPaint = paintWithColor(DEFAULT_CHAPTER_SELECTED_COLOR)

    private val graphFillPaint = Paint().also {
        it.style = Paint.Style.FILL
    }

    private val graphOverlayPaint = paintWithColor(DEFAULT_GRAPH_OVERLAY_COLOR).also {
        it.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    }

    private val clearPaint = Paint().also {
        it.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val scrubberDisabledSize by lazy { dpToPx(SCRUBBER_DISABLED_SIZE_DP) }
    private val scrubberEnabledSize by lazy { dpToPx(SCRUBBER_ENABLED_SIZE_DP) }
    private val scrubberDraggedSize by lazy { dpToPx(SCRUBBER_DRAGGED_SIZE_DP) }

    private val barHeight by lazy { dpToPx(TIME_BAR_HEIGHT_DP) }
    private val barBottomMargin by lazy { dpToPx(TIME_BAR_BOTTOM_MARGIN_DP) }

    private val initEdgeSize by lazy { dpToPx(INIT_EDGE_DP) }
    private val touchTargetHeight by lazy { dpToPx(TOUCH_TARGET_HEIGHT_DP) }
    private val retentionGraphHeight by lazy { dpToPx(RETENTION_GRAPH_HEIGHT_DP) }
    private val retentionGraphBottomMargin by lazy { dpToPx(RETENTION_GRAPH_BOTTOM_MARGIN_DP) }

    private val additionDragOffset by lazy { dpToPx(ADDITIONAL_DRAG_OFFSET_DP) }

    private val chapterThicknessDelta by lazy { dpToPx(CHAPTER_THICKNESS_DELTA_DP) }
    private val defaultGapSize by lazy { dpToPx(GAP_SIZE_DP) }

    private val scrubListeners: ArrayList<TimeBar.OnScrubListener> = arrayListOf()

    private var showScrubber = false

    //  Left position, relative to parent
    private var positionLeft = 0

    private var isScrubbing = false
        set(value) {
            field = value
            calculateGapChapters()
        }

    private val barTop: Int
        get() = (progressBar.centerY() - progressBar.height().toFloat().div(2)).toInt()

    private val barBottom: Int
        get() = barTop + progressBar.height()

    var chapters = listOf<Chapter>()
        set(value) {
            val sorted = value.sortedBy { it.startTimeMs }.toMutableList()

            field = if (sorted.size > 0) sorted else emptyList()
            calculateGapChapters()
            invalidate()
        }

    var moments = listOf<Moment>()
        set(value) {
            val sorted = value.sortedBy { it.timestamp }.toMutableList()
            field = if (sorted.size > 0) sorted else emptyList()
            invalidate()
        }

    private var adjustedGraphPoints: Array<RetentionGraphPoint> = arrayOf()

    var retentionGraphData = listOf<RetentionGraphEntry>()
        set(value) {
            field = value.sortedBy { it.x }
            adjustRetentionGraphPoints()
            invalidate()
        }

    private var currentMoment: Moment? = null
    private var currentChapter: Chapter? = null

    private var timeBarPreview: BunnyTimeBarPreview? = null

    private var gapChapters: List<Gap> = emptyList()

    private val touchPosition = Point()
    private val displayMetrics = context.resources.displayMetrics
    private val density = displayMetrics.density

    private val scrubberRadius: Int
        get() = when {
            isScrubbing || isFocused -> scrubberDraggedSize
            isEnabled -> scrubberEnabledSize
            else -> scrubberDisabledSize
        }.div(2)

    private val scrubberPositionScreen: Int
        get() = positionLeft + getScreenScrubber()

    var tintColor: Int = Color.WHITE
        set(value) {
            field = value
            playedPaint.color = tintColor
            scrubberCirclePaint.color = tintColor
            chapterSelectedPaint.color = tintColor
            bufferedPaint.color = ColorUtils.setAlphaComponent(tintColor, 150)
            unPlayedPaint.color = ColorUtils.setAlphaComponent(tintColor, 80)
            invalidate()
        }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                startPreview(
                    position
                        .coerceAtLeast(0)
                        .coerceAtMost(max(getDuration(), 0))
                )
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                val positionSanitized = position
                    .coerceAtLeast(0)
                    .coerceAtMost(max(getDuration(), 0))
                updatePreview(positionSanitized)
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                timeBarPreview?.hide()
            }
        })

        if (isInEditMode) {
            setDuration(TimeUnit.MINUTES.toMillis(100))
            setPosition(TimeUnit.MINUTES.toMillis(15))
            setBufferedPosition(TimeUnit.MINUTES.toMillis(40))
            showScrubber()
            updateValues()
            invalidate()
        }
    }

    fun showScrubber() {
        showScrubber = true
        invalidate()
    }

    fun hideScrubber() {
        showScrubber = false
        invalidate()
    }

    override fun addListener(listener: TimeBar.OnScrubListener) {
        Assertions.checkNotNull(listener)
        scrubListeners.add(listener)
    }

    override fun removeListener(listener: TimeBar.OnScrubListener) {
        Assertions.checkNotNull(listener)
        scrubListeners.remove(listener)
    }

    private fun startScrubbing(scrubPosition: Long) {
        this.scrubPosition = scrubPosition

        isScrubbing = true
        isPressed = true
        parent?.requestDisallowInterceptTouchEvent(true)

        for (listener in scrubListeners) {
            listener.onScrubStart(this, scrubPosition)
        }
    }

    private fun updateScrubbing(scrubPosition: Long) {
        if (this.scrubPosition == scrubPosition)
            return

        this.scrubPosition = scrubPosition

        for (listener in scrubListeners) {
            listener.onScrubMove(this, scrubPosition)
        }
    }

    private fun stopScrubbing(canceled: Boolean) {
        isScrubbing = false
        isPressed = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()

        for (listener in scrubListeners) {
            listener.onScrubStop(this, scrubPosition, canceled)
        }
    }

    fun timeBarPreview(timeBarPreview: BunnyTimeBarPreview?) = apply {
        this.timeBarPreview = timeBarPreview
    }

    private fun startPreview(scrubPosition: Long) {
        if (chapters.isNotEmpty()) {
            val chapter = getChapterAt(scrubPosition)
            if (currentChapter != chapter) { onChapterChanged(chapter) }
        }

        if (moments.isNotEmpty()) {
            val moment = getMomentAt(scrubPosition)
            if (moment != currentMoment) { onMomentChanged(moment) }
        }

        timeBarPreview?.let {
            it.updatePosition(scrubberPositionScreen, scrubPosition)
            it.show()
            it.updateTime(scrubPosition)
        }
    }

    private fun updatePreview(scrubPosition: Long) {
        if (chapters.isNotEmpty()) {
            val chapter = getChapterAt(scrubPosition)
            if (currentChapter != chapter) { onChapterChanged(chapter) }
        }

        if (moments.isNotEmpty()) {
            val moment = getMomentAt(scrubPosition)
            if (moment != currentMoment) { onMomentChanged(moment) }
        }

        timeBarPreview?.let {
            it.updatePosition(scrubberPositionScreen, scrubPosition)
            it.updateTime(scrubPosition)
        }
    }

    private fun getChapterAt(position: Long): Chapter? {
        return chapters.firstOrNull { position in it.startTimeMs..it.endTimeMs }
    }

    private fun getMomentAt(position: Long): Moment? {
        return moments.firstOrNull {
            position in it.timestamp - MOMENT_POSITION_PADDING_MILLIS .. it.timestamp + MOMENT_POSITION_PADDING_MILLIS
        }
    }

    private fun onChapterChanged(newChapter: Chapter?) {
        currentChapter = newChapter
        updateTimeBarPreviewTitle()
    }

    private fun onMomentChanged(moment: Moment?) {
        currentMoment = moment
        updateTimeBarPreviewTitle()
    }

    private fun updateTimeBarPreviewTitle(){
        timeBarPreview?.updateTitle(currentChapter?.title, currentMoment?.label)
    }

    private fun getDuration(): Long {
        return if (duration == C.TIME_UNSET) -1 else duration
    }

    override fun setPosition(position: Long) {
        if (this.position != position) {
            this.position = position
            updateValues()
            invalidate()

            if (!isScrubbing) {
                if (chapters.isNotEmpty()) {
                    val chapter = getChapterAt(position)
                    if (currentChapter != chapter) { onChapterChanged(chapter) }
                }

                if (moments.isNotEmpty()) {
                    val moment = getMomentAt(scrubPosition)
                    if (moment != currentMoment) { onMomentChanged(moment) }
                }
            }
        }
    }

    override fun setBufferedPosition(bufferedPosition: Long) {
        if (this.bufferedPosition != bufferedPosition) {
            this.bufferedPosition = bufferedPosition
            updateValues()
            invalidate()
        }
    }

    override fun setDuration(duration: Long) {
        if (isScrubbing && duration == C.TIME_UNSET) {
            stopScrubbing(true)
        }
        if (this.duration != duration) {
            this.duration = duration
            calculateGapChapters()
            updateValues()
            invalidate()
        }
    }

    override fun getPreferredUpdateDelay(): Long {
        val timeBarWidthDp = pxToDp(progressBar.width())
        return if (timeBarWidthDp == 0 || duration <= 0L) Long.MAX_VALUE
        else duration / timeBarWidthDp
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.save()
        drawRetentionGraph(canvas)
        drawTimeBar(canvas)
        drawMoments(canvas)
        drawScrubber(canvas)
        canvas.restore()
    }

    private fun drawTimeBar(canvas: Canvas) {
        if (duration <= 0) {
            canvas.drawRect(
                /* left = */ seekBounds.left.toFloat(),
                /* top = */ barTop.toFloat(),
                /* right = */ seekBounds.right.toFloat(),
                /* bottom = */ barBottom.toFloat(),
                /* paint = */ unPlayedPaint
            )
            return
        }

        canvas.drawRect(
            /* left = */ seekBounds.left.toFloat(),
            /* top = */ barTop.toFloat(),
            /* right = */ progressBar.left.toFloat(),
            /* bottom = */ barBottom.toFloat(),
            /* paint = */ playedPaint
        )

        canvas.drawRect(
            /* left = */ progressBar.right.toFloat(),
            /* top = */ barTop.toFloat(),
            /* right = */ seekBounds.right.toFloat(),
            /* bottom = */ barBottom.toFloat(),
            /* paint = */ if (position >= duration) playedPaint else unPlayedPaint
        )

        drawDefaultTimeBar(canvas)

        if (chapters.isNotEmpty()) {
            drawChapters(canvas)
        }
    }

    private fun drawMoments(canvas: Canvas) {
        for (point in moments) {
            val xPosition = screenPositionOnProgressBar(point.timestamp)

            val radius = MOMENT_POINT_SIZE / 2f

            canvas.drawCircle(
                /* cx = */ xPosition.toFloat(),
                /* cy = */ progressBar.centerY().toFloat(),
                /* radius = */ radius,
                /* paint = */ momentsPaint,
            )
        }
    }

    private fun calculateGapChapters() {
        gapChapters = chapters.map {
            Gap(
                startTimeMs = it.startTimeMs, endTimeMs = it.endTimeMs,
                startScreenPosition = (screenPositionOnProgressBar(it.startTimeMs) + defaultGapSize).toFloat(),
                endScreenPosition = screenPositionOnProgressBar(it.endTimeMs).toFloat()
            )
        }
    }

    private fun drawDefaultTimeBar(canvas: Canvas) {
        canvas.drawRect(
            /* left = */ (if (isScrubbing) seekBounds.left else progressBar.left).toFloat(),
            /* top = */ barTop.toFloat(),
            /* right = */ progressBar.right.toFloat(),
            /* bottom = */ barBottom.toFloat(),
            /* paint = */ unPlayedPaint
        )

        // Buffered
        canvas.drawRect(
            /* left = */ (if (isScrubbing) seekBounds.left else progressBar.left).toFloat(),
            /* top = */ barTop.toFloat(),
            /* right = */ bufferedBar.right.toFloat() - if (isScrubbing) initEdgeSize else 0,
            /* bottom = */ barBottom.toFloat(),
            /* paint = */ bufferedPaint
        )

        // Played
        canvas.drawRect(
            /* left = */ (if (isScrubbing) seekBounds.left else progressBar.left).toFloat(),
            /* top = */ barTop.toFloat(),
            /* right = */ positionBar.right.toFloat() - if (isScrubbing) initEdgeSize else 0,
            /* bottom = */ barBottom.toFloat(),
            /* paint = */ playedPaint
        )
    }

    private fun drawChapters(canvas: Canvas) {
        gapChapters.forEach { gapHelper ->
            // gap to the left of chapter
            canvas.drawRect(
                /* left = */ gapHelper.startScreenPosition - defaultGapSize,
                /* top = */ barTop.toFloat(),
                /* right = */ gapHelper.startScreenPosition,
                /* bottom = */ barBottom.toFloat(),
                /* paint = */ clearPaint)

            // gap to the right of chapter
            canvas.drawRect(
                /* left = */ gapHelper.endScreenPosition,
                /* top = */ barTop.toFloat(),
                /* right = */ gapHelper.endScreenPosition + defaultGapSize,
                /* bottom = */ barBottom.toFloat(),
                /* paint = */ clearPaint)

            if (isScrubbing && scrubPosition in gapHelper.startTimeMs..gapHelper.endTimeMs) {
                canvas.drawRect(
                    /* left = */ gapHelper.startScreenPosition,
                    /* top = */ (barTop - chapterThicknessDelta).toFloat(),
                    /* right = */ gapHelper.endScreenPosition,
                    /* bottom = */ (barBottom + chapterThicknessDelta).toFloat(),
                    /* paint = */ chapterSelectedPaint
                )
            }
        }
    }

    private fun drawScrubber(canvas: Canvas) {
        if (duration <= 0  || !showScrubber)
            return

        canvas.drawCircle(
            /* cx = */ getScreenScrubber().toFloat(),
            /* cy = */ scrubberBar.centerY().toFloat(),
            /* radius = */ scrubberRadius.toFloat(),
            /* paint = */ scrubberCirclePaint
        )
    }

    private fun drawRetentionGraph(canvas: Canvas) {
        if(adjustedGraphPoints.isEmpty()) {
            return
        }

        buildRetentionGraphPath(retentionGraphFillPath)

        retentionGraphFillPath.lineTo(graphBounds.right.toFloat(), graphBounds.bottom.toFloat())
        retentionGraphFillPath.lineTo(graphBounds.left.toFloat(), graphBounds.bottom.toFloat())
        retentionGraphFillPath.lineTo(graphBounds.left.toFloat(), adjustedGraphPoints[0].y)
        retentionGraphFillPath.close()

        canvas.drawPath(retentionGraphFillPath, graphFillPaint)

        retentionGraphOverlayBounds.left = graphBounds.left
        retentionGraphOverlayBounds.top = graphBounds.top
        retentionGraphOverlayBounds.right = getScreenScrubber()
        retentionGraphOverlayBounds.bottom = graphBounds.bottom

        canvas.drawRect(retentionGraphOverlayBounds, graphOverlayPaint)
    }

    private fun updateValues() {
        positionBar.set(progressBar)
        bufferedBar.set(progressBar)
        scrubberBar.set(progressBar)

        if (duration > 0) {
            positionBar.right = screenPositionOnProgressBar(position)
            bufferedBar.right = screenPositionOnProgressBar(bufferedPosition)
            scrubberBar.right = getScreenScrubber()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (!isEnabled || duration <= 0 || event == null) {
            return false
        }

        val touchPosition = resolveTouchPosition(event)
        val x = touchPosition.x
        val y = touchPosition.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> if (seekBounds.contains(x, y)) {
                startScrubbing(positionOnProgressBarMillis(x))
                updateValues()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> if (isScrubbing) {
                updateScrubbing(positionOnProgressBarMillis(x))
                updateValues()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (isScrubbing) {
                stopScrubbing(event.action == MotionEvent.ACTION_CANCEL)
                return true
            }
            else -> { /* no-op */ }
        }
        return false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.UNSPECIFIED -> touchTargetHeight + retentionGraphHeight
            MeasureSpec.EXACTLY -> heightSize
            else -> (touchTargetHeight + retentionGraphHeight).coerceAtMost(heightSize)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    @SuppressLint("DrawAllocation")
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        positionLeft = left

        val width: Int = right - left
        val height: Int = bottom - top
        val barY: Int = (height - touchTargetHeight) - barBottomMargin

        val seekLeft: Int = paddingLeft
        val seekRight: Int = width - paddingRight
        val progressY: Int = barY + (touchTargetHeight - barHeight) / 2

        seekBounds.set(
            /* left = */ seekLeft,
            /* top = */ barY,
            /* right = */ seekRight,
            /* bottom = */ barY + touchTargetHeight
        )

        progressBar.set(
            /* left = */ seekBounds.left + initEdgeSize,
            /* top = */ progressY,
            /* right = */ seekBounds.right - initEdgeSize,
            /* bottom = */ progressY + barHeight
        )

        graphBounds.set(
            /* left = */ left,
            /* top = */ top,
            /* right = */ right,
            /* bottom = */ progressY - retentionGraphBottomMargin
        )

        calculateGapChapters()
        updateValues()
        adjustRetentionGraphPoints()
        updateGraphGradient()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (isScrubbing && !enabled) {
            stopScrubbing(true)
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (isScrubbing && !gainFocus) {
            stopScrubbing(false)
        }
    }

    private fun resolveTouchPosition(motionEvent: MotionEvent): Point {
        touchPosition.set(motionEvent.x.toInt(), motionEvent.y.toInt())
        return touchPosition
    }

    private fun dpToPx(dps: Int): Int {
        return (dps * density + 0.5f).toInt()
    }

    private fun pxToDp(px: Int): Int {
        return (px / density).toInt()
    }

    private fun screenPositionOnProgressBar(millis: Long): Int {
        return progressBar.left.plus(progressBar.width().times(millis.toFloat().div(duration))).toInt()
            .coerceAtLeast(progressBar.left)
            .coerceAtMost(progressBar.right)
    }

    private fun getScreenScrubber(): Int {
        return if (isScrubbing) {
            additionalOffsetFromTouch()
                .coerceAtLeast(seekBounds.left + scrubberRadius)
                .coerceAtMost(seekBounds.right - scrubberRadius)
        } else {
            positionBar.right
                .coerceAtLeast(seekBounds.left + scrubberRadius)
                .coerceAtMost(seekBounds.right - scrubberRadius)
        }
    }

    private fun additionalOffsetFromTouch(): Int {
        val progressPercentage = touchPosition.x.toFloat().minus(progressBar.left).div(progressBar.width())
        val relPosOnSeekBounds = seekBounds.width().toFloat().times(progressPercentage)
            .coerceAtLeast(0f).toInt()

        return relPosOnSeekBounds.plus(seekBounds.left)
            .coerceAtLeast(seekBounds.left).coerceAtMost(seekBounds.right)
    }


    private fun positionOnProgressBarMillis(xPosition: Int): Long {
        val newX = xPosition - progressBar.left - additionDragOffset
        val newWidth = progressBar.width() - additionDragOffset.times(2)

        val relativePercent = newX.toFloat() / newWidth
        val relativeXPosition = progressBar.left + relativePercent.times(progressBar.width())

        return relativeXPosition.minus(progressBar.left).div(progressBar.width()).times(
            duration
        )
            .coerceAtLeast(0f)
            .coerceAtMost(duration.toFloat()).toLong()
    }

    override fun setKeyTimeIncrement(time: Long) {
        // accessibility, no-op for now
    }

    override fun setKeyCountIncrement(count: Int) {
        // accessibility, no-op for now
    }

    override fun setAdGroupTimesMs(
        adGroupTimesMs: LongArray?,
        playedAdGroups: BooleanArray?,
        adGroupCount: Int
    ) {
        // no-op
    }

    private fun paintWithColor(color: Int): Paint {
        val paint = Paint()
        paint.color = color
        return paint
    }

    private fun adjustRetentionGraphPoints() {
        if(retentionGraphData.isEmpty()) {
            return
        }

        val chartHeight = graphBounds.bottom - graphBounds.top
        val chartWidth = graphBounds.right - graphBounds.left

        var maxY = 0f

        for (p in retentionGraphData) {
            if (p.y > maxY) {
                maxY = p.y.toFloat()
            }
        }

        val scaleY = chartHeight / maxY
        val axesSpan = retentionGraphData[retentionGraphData.size - 1].x - retentionGraphData[0].x
        val startX = retentionGraphData[0].x
        for (i in retentionGraphData.indices) {
            val p = retentionGraphData[i]
            val newX = (p.x - startX) * chartWidth / axesSpan + graphBounds.left
            val newY = chartHeight - p.y * scaleY
            adjustedGraphPoints += RetentionGraphPoint(newX.toFloat(), newY)
        }
    }

    private fun buildRetentionGraphPath(path: Path) {
        path.reset()
        path.moveTo(adjustedGraphPoints[0].x, adjustedGraphPoints[0].y)

        val pointSize = adjustedGraphPoints.size
        for (i in 0 until adjustedGraphPoints.size - 1) {
            val pointX = (adjustedGraphPoints[i].x + adjustedGraphPoints[i + 1].x) / 2
            val pointY = (adjustedGraphPoints[i].y + adjustedGraphPoints[i + 1].y) / 2
            val controlX = adjustedGraphPoints[i].x
            val controlY = adjustedGraphPoints[i].y
            path.quadTo(controlX, controlY, pointX, pointY)
        }
        path.quadTo(
            adjustedGraphPoints[pointSize - 1].x,
            adjustedGraphPoints[pointSize - 1].y,
            adjustedGraphPoints[pointSize - 1].x,
            adjustedGraphPoints[pointSize - 1].y
        )
    }

    private fun updateGraphGradient(){
        val gradientBitmap = createGradientBitmap(graphBounds.bottom)
        graphFillPaint.shader = BitmapShader(gradientBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun createGradientBitmap(height: Int): Bitmap {
        val bitmap = createBitmap(1, height)
        val canvas = Canvas(bitmap)

        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(DEFAULT_GRAPH_GRADIENT_START_COLOR, DEFAULT_GRAPH_GRADIENT_END_COLOR),
            null,
            Shader.TileMode.CLAMP
        )

        val paint = Paint().apply {
            shader = gradient
        }

        canvas.drawRect(0f, 0f, 1f, height.toFloat(), paint)

        return bitmap
    }

    private data class Gap(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val startScreenPosition: Float,
        val endScreenPosition: Float,
    )

    private data class RetentionGraphPoint(
        val x: Float,
        val y: Float
    )
}