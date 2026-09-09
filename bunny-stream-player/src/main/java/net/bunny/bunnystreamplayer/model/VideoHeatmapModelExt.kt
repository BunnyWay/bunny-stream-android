package net.bunny.bunnystreamplayer.model

import android.text.TextUtils

/**
 * Turns the API's retention heatmap into offsets the timeline can draw.
 *
 * The keys arrive as strings and are not all usable: the API includes a `"-1"` bucket, and any
 * non-numeric key would blow up `toInt()`. Both are dropped rather than crashing the player over a
 * decoration.
 */
fun Map<String, Int>.getSanitizedRetentionData(): Map<Int, Int> =
    filter { it.key != "-1" }
        .filter { TextUtils.isDigitsOnly(it.key) }
        .mapKeys { it.key.toInt() }
