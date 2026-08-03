package net.bunny.api.video.domain.model

/**
 * Viewing statistics for a video over a requested date range.
 *
 * The charts are keyed by date as the API returns them (`"2026-07-01"`), so they can be fed to a
 * chart library without reformatting.
 *
 * @property viewsChart views per day.
 * @property watchTimeChart watch time per day, in seconds.
 * @property countryViewCounts views per ISO country code.
 * @property countryWatchTime watch time per ISO country code, in seconds.
 * @property engagementScore Bunny's aggregate engagement score, `0..100`.
 */
public data class VideoStatistics(
    val viewsChart: Map<String, Long>,
    val watchTimeChart: Map<String, Long>,
    val countryViewCounts: Map<String, Long>,
    val countryWatchTime: Map<String, Long>,
    val engagementScore: Int,
)
