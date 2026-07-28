package net.bunny.bunnystreamplayer.model

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.parcelize.Parcelize
import net.bunny.player.R

/**
 * Drawable overrides for the player controls. Assign an instance to
 * [net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer.iconSet] to replace any of the default icons
 * with your own drawables. Every field defaults to the SDK icon, so override only what you need:
 *
 * ```kotlin
 * player.iconSet = PlayerIconSet(
 *     playIcon = R.drawable.my_play,
 *     pauseIcon = R.drawable.my_pause,
 * )
 * ```
 *
 * The set carries over to the fullscreen player automatically.
 *
 * @property playIcon center play button.
 * @property pauseIcon center pause button.
 * @property rewindIcon skip-back button (10 seconds).
 * @property forwardIcon skip-forward button (10 seconds).
 * @property settingsIcon settings (quality/speed/captions) menu button.
 * @property volumeOnIcon mute toggle in the unmuted state.
 * @property volumeOffIcon mute toggle in the muted state.
 * @property fullscreenOnIcon enter-fullscreen button.
 * @property fullscreenOffIcon exit-fullscreen button.
 */
@Parcelize
data class PlayerIconSet(
    @DrawableRes
    val playIcon: Int = R.drawable.ic_play_48dp,

    @DrawableRes
    val pauseIcon: Int = R.drawable.ic_pause_48dp,

    @DrawableRes
    val rewindIcon: Int = R.drawable.ic_replay_10s_48dp,

    @DrawableRes
    val forwardIcon: Int = R.drawable.ic_forward_10s_48dp,

    @DrawableRes
    val settingsIcon: Int = R.drawable.ic_settings_24dp,

    @DrawableRes
    val volumeOnIcon: Int = R.drawable.ic_volume_on_24dp,

    @DrawableRes
    val volumeOffIcon: Int = R.drawable.ic_volume_off_24dp,

    @DrawableRes
    val fullscreenOnIcon: Int = R.drawable.ic_fullscreen_24dp,

    @DrawableRes
    val fullscreenOffIcon: Int = R.drawable.ic_fullscreen_exit_24dp,
) : Parcelable
