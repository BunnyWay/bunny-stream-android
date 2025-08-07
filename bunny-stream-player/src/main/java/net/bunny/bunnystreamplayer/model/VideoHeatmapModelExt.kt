package net.bunny.bunnystreamplayer.model

import android.text.TextUtils
import org.openapitools.client.models.VideoHeatmapModel

fun VideoHeatmapModel.getSanitizedRetentionData(): Map<Int, Int> {
    return heatmap
        ?.filter { it.key != "-1" } // Keys are seconds on video timeline, why negative key?
        ?.filter { TextUtils.isDigitsOnly(it.key) }
        ?.mapKeys { it.key.toInt() }
        ?: mapOf()
}