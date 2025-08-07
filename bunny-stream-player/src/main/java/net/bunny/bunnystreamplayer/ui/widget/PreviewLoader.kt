package net.bunny.bunnystreamplayer.ui.widget

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.target.Target
import net.bunny.bunnystreamplayer.common.GlideThumbnailTransformation
import net.bunny.bunnystreamplayer.model.SeekThumbnail

class PreviewLoader(
    private val context: Context,
    private val seekThumbnail: SeekThumbnail,
) {
    fun loadPreview(currentPosition: Long, previewImageView: ImageView) {
        val currentFrameGlobal = (currentPosition / seekThumbnail.frameDurationPerThumbnail).toInt()
        val jpgIndex = currentFrameGlobal / seekThumbnail.thumbnailsPerImage
        val safeJpgIndex = jpgIndex.coerceIn(0, (seekThumbnail.seekThumbnailUrls.size) - 1)
        val currentFrameLocal = currentFrameGlobal % seekThumbnail.thumbnailsPerImage
        val currentPositionWithinJpg = (currentFrameLocal * seekThumbnail.frameDurationPerThumbnail).toLong()

        val glideUrl = GlideUrl(seekThumbnail.seekThumbnailUrls[safeJpgIndex]) {
            mapOf("Referer" to "https://iframe.mediadelivery.net/")
        }
        Glide
            .with(context)
            .load(glideUrl)
            .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
            .transform(
                GlideThumbnailTransformation(
                    currentPositionWithinJpg,
                    seekThumbnail.frameDurationPerThumbnail,
                )
            )
            .into(previewImageView)
    }
}
