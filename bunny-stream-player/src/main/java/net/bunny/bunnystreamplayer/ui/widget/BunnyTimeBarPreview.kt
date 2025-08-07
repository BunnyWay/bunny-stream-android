package net.bunny.bunnystreamplayer.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.use
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import net.bunny.player.R
import java.util.Formatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

@UnstableApi class BunnyTimeBarPreview(
    context: Context, private val attrs: AttributeSet?
) : LinearLayout(context, attrs) {

    companion object {
        private const val ANIM_DURATION_MS = 300L
    }

    interface PreviewListener {
        fun loadThumbnail(imageView: ImageView, position: Long)
    }

    private val rootLayout by lazy { findViewById<LinearLayout>(R.id.root_layout) }
    private val thumbImageView by lazy { findViewById<ImageView>(R.id.thumb) }
    private val titleTextView by lazy { findViewById<TextView>(R.id.preview_title) }
    private val timeTextView by lazy { findViewById<TextView>(R.id.timestamp) }
    private val thumbWrapper by lazy { findViewById<FrameLayout>(R.id.thumb_wrapper) }
    private val textWrapper by lazy { findViewById<LinearLayout>(R.id.text_wrapper) }

    private val formatBuilder: StringBuilder = StringBuilder()
    private val formatter: Formatter = Formatter(formatBuilder, Locale.getDefault())

    private var latestScrub: Int = 0
    private var latestRect: Rect? = null
    private var latestNormedPosition = -1L
    private val positionDiff = TimeUnit.SECONDS.toMillis(20)

    private val View.screenLocation
        get(): IntArray {
            val point = IntArray(2)
            getLocationOnScreen(point)
            return point
        }

    private val View.boundingBox
        get(): Rect {
            val (x, y) = screenLocation
            return Rect(x, y, x + width, y + height)
        }

    private val View.center: Float
        get() = this.width / 2.0f


    private var listener: PreviewListener? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.bunny_timebar_preview, this, true)

        if (!isInEditMode) {
            rootLayout.visibility = View.GONE
        }

        if (attrs != null) {
            processAttrs()
        }
    }

    fun previewListener(listener: PreviewListener) = apply {
        this.listener = listener
    }

    fun hide(duration: Long = ANIM_DURATION_MS) {
        with(rootLayout) {
            animate().cancel()
            alpha = 1f
            visibility = View.VISIBLE

            animate().let {
                it.duration = duration
                it.setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        rootLayout.alpha = 0f
                        rootLayout.visibility = View.GONE
                        this@BunnyTimeBarPreview.visibility = View.GONE
                    }
                })
                it.interpolator = FastOutSlowInInterpolator()
                it.alpha(0f)
                it.start()
            }
        }
    }

    fun show(duration: Long = ANIM_DURATION_MS) {
        visibility = View.VISIBLE
        with(rootLayout) {
            animate().setListener(null).cancel()
            alpha = 0f
            visibility = View.VISIBLE

            animate().let {
                it.duration = duration
                it.alpha(1f)
                it.interpolator = FastOutSlowInInterpolator()
                it.start()
            }
        }
    }

    fun updateTypeface(typeface: Typeface) {
        titleTextView.typeface = typeface
        timeTextView.typeface = typeface
    }

    internal fun updateTitle(chapterTitle: String?, momentTitle: String?) {
        val title = momentTitle ?: chapterTitle ?: ""
        titleTextView.text = title
    }

    internal fun updateTime(millis: Long) = apply {
        Util.getStringForTime(formatBuilder, formatter, millis).let {
            if (timeTextView.text != it) {
                timeTextView.text = it
            }
        }
    }

    private fun processAttrs() {
        context.obtainStyledAttributes(attrs, R.styleable.BunnyTimeBarPreview, 0, 0).use {
            val previewWidth = it.getDimensionPixelSize(
                R.styleable.BunnyTimeBarPreview_bt_preview_width,
                context.resources.getDimensionPixelSize(R.dimen.bt_timebar_preview_width)
            )

            val previewHeight = it.getDimensionPixelSize(
                R.styleable.BunnyTimeBarPreview_bt_preview_height,
                context.resources.getDimensionPixelSize(R.dimen.bt_timebar_preview_height)
            )

            val chapterMaxWidth = it.getDimensionPixelSize(
                R.styleable.BunnyTimeBarPreview_bt_chapter_title_max_width,
                context.resources.getDimensionPixelSize(R.dimen.bt_timebar_chapter_max_width)
            )

            titleTextView.maxWidth = chapterMaxWidth
            thumbWrapper.layoutParams = LayoutParams(previewWidth, previewHeight)
        }
    }

    internal fun updatePosition(scrubberScreenPosition: Int, playbackPosition: Long) {
        latestScrub = scrubberScreenPosition
        if (isInInterval(playbackPosition)) {
            latestNormedPosition = playbackPosition
            listener?.loadThumbnail(thumbImageView, playbackPosition)
        }
    }

    private fun isInInterval(newPosition: Long): Boolean {
        return with(latestNormedPosition) {
            this == -1L || newPosition in this.minus(positionDiff)..this.plus(positionDiff)
        }
    }

    private fun movePreview(pos: Int) {
        val min = min(thumbWrapper.left, textWrapper.left)
        val max = ((parent as ViewGroup).width - width)
        val centerOfScrubber = (pos.toFloat() - center).toInt()

        val containerPos = centerOfScrubber
            .coerceAtLeast(min)
            .coerceAtMost(max)

        when (containerPos) {
            min -> moveThumbStart()
            max -> moveThumbEnd()
            else -> resetThumAndTimestampPosition()
        }
        x = containerPos.toFloat()
    }

    private fun moveThumbStart() {
        if (thumbWrapper.width >= textWrapper.width) {
            resetThumAndTimestampPosition()
            return
        }

        val minimum = min(thumbWrapper.left, textWrapper.left).toFloat()
        val thumbPos = (latestScrub - thumbWrapper.center - paddingLeft).coerceAtLeast(minimum)

        thumbWrapper.x = thumbPos
        centerTimeTextView()
    }

    private fun moveThumbEnd() {
        if (thumbWrapper.width >= textWrapper.width) {
            resetThumAndTimestampPosition()
            return
        }

        val maximum = min(
            width - thumbWrapper.center,
            textWrapper.right.toFloat() - thumbWrapper.center
        )

        val scrubRelative = latestScrub.toFloat() - (parent as ViewGroup).width + width - paddingRight
        val thumbPos = min(scrubRelative, maximum)

        thumbWrapper.x = thumbPos - thumbWrapper.center
        centerTimeTextView()
    }

    private fun resetThumAndTimestampPosition() {
        thumbWrapper.x = thumbWrapper.left.toFloat()
        timeTextView.x = timeTextView.left.toFloat()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        movePreview(latestScrub)
        if (latestRect != boundingBox) {
            // To reduce the callback only notify if the view bounds are changed
            boundingBox.let {
                it.left += paddingLeft
                it.right -= paddingRight
                latestRect = it
            }
        }
    }

    private fun centerTimeTextView() {
        val centerThumbnailContainer = thumbWrapper.x + thumbWrapper.center - thumbWrapper.paddingLeft
        timeTextView.x = centerThumbnailContainer - timeTextView.center
    }
}
