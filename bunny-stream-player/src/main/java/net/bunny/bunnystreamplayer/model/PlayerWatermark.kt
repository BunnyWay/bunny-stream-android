package net.bunny.bunnystreamplayer.model

import android.os.Parcelable
import android.util.Patterns
import kotlinx.parcelize.Parcelize

/**
 * A logo/watermark overlaid on top of the video while it plays.
 *
 * This is a client-side overlay only. Bunny's backend does not return watermark metadata to the
 * player; the model lets an integrator render an additional image on top of any
 * [net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer] or
 * [net.bunny.bunnystreamplayer.livestream.BunnyLiveStreamPlayer].
 */
@Parcelize
public data class PlayerWatermark(
    val imageUrl: String,
    val position: Position = Position.TOP_TRAILING,
    val relativeWidth: Float = DEFAULT_RELATIVE_WIDTH,
    val opacity: Float = DEFAULT_OPACITY,
    val marginDp: Float = DEFAULT_MARGIN_DP,
) : Parcelable {

    /** Where the watermark is pinned within the player bounds. */
    public enum class Position {
        TOP_LEADING,
        TOP_TRAILING,
        BOTTOM_LEADING,
        BOTTOM_TRAILING,
        CENTER,
    }

    init {
        require(imageUrl.isNotBlank()) {
            "imageUrl must not be blank"
        }
        require(
            imageUrl.startsWith("http://", ignoreCase = true) ||
                imageUrl.startsWith("https://", ignoreCase = true),
        ) {
            "imageUrl must be an HTTP(S) URL, was: '$imageUrl'"
        }
        require(relativeWidth > 0f && relativeWidth <= 1f) {
            "relativeWidth must be in (0, 1], was: $relativeWidth"
        }
        require(opacity in 0f..1f) {
            "opacity must be in [0, 1], was: $opacity"
        }
        require(marginDp >= 0f) {
            "marginDp must be non-negative, was: $marginDp"
        }
    }

    private companion object {
        private const val DEFAULT_RELATIVE_WIDTH = 0.18f
        private const val DEFAULT_OPACITY = 0.85f
        private const val DEFAULT_MARGIN_DP = 12f
    }
}
