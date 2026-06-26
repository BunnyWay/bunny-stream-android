
# VideoStatisticsModel

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**viewsChart** | **kotlin.collections.Map&lt;kotlin.String, kotlin.Long&gt;** | Shows the number of playback starts over time, with each data point representing the count of view starts in the corresponding UTC interval (hourly or daily). It is available at both the library level (aggregated across all videos) and the video level (for a single video). A playback start is counted once per viewer session per video and empty intervals are shown as zero. |  [optional]
**watchTimeChart** | **kotlin.collections.Map&lt;kotlin.String, kotlin.Long&gt;** | Shows the cumulative time viewers spent watching over time, with each data point representing total watch time in the corresponding UTC interval (hourly or daily). It is available at both the library level (aggregated across all videos) and the video level (for a single video). Rewatches and repeated visits add to the total and empty intervals are shown as zero. |  [optional]
**countryViewCounts** | **kotlin.collections.Map&lt;kotlin.String, kotlin.Long&gt;** | Shows the total number of playback starts by country for the selected time range. It is available at both the library level (aggregated across all videos) and the video level (for a single video). Each country’s value reflects unique playback starts per viewer session based on IP geolocation at playback start. |  [optional]
**countryWatchTime** | **kotlin.collections.Map&lt;kotlin.String, kotlin.Long&gt;** | Shows the total watch time by country for the selected time range. It is available at both the library level (aggregated across all videos) and the video level (for a single video). Values reflect cumulative viewing time (rewatches and repeated visits add to the total) based on IP geolocation at playback start. |  [optional]
**engagementScore** | **kotlin.Int** | Indicates how engaging a specific video is on a 0–100 scale based on viewing duration and viewing patterns; higher values reflect stronger viewer retention. Reported at the video level only and may be unavailable when there isn’t enough viewing data. |  [optional]



