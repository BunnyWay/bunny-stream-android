package net.bunny.bunnystreamcameraupload.domain

import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun Long.toFormattedDuration(): String {
    val duration = this.toDuration(DurationUnit.MILLISECONDS)
    val h = duration.inWholeSeconds / 3600
    val m = duration.inWholeSeconds % 3600 / 60
    val s = duration.inWholeSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}