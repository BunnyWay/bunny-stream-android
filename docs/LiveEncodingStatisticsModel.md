
# LiveEncodingStatisticsModel

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**hourly** | **kotlin.Boolean** | Indicates whether the chart uses hourly or daily UTC buckets. |  [optional]
**totalEncodingSeconds** | **kotlin.Long** | The total number of live encoding seconds in the selected period. |  [optional]
**encodingSecondsChart** | **kotlin.collections.Map&lt;kotlin.String, kotlin.Long&gt;** | Live encoding seconds over time for the selected period, grouped hourly or daily in UTC. |  [optional]
**encodingSecondsByLiveStream** | **kotlin.collections.Map&lt;kotlin.String, kotlin.Long&gt;** | Live encoding seconds grouped by live stream ID. |  [optional]
**encodingSecondsByVideoCodec** | **kotlin.collections.Map&lt;kotlin.String, kotlin.Long&gt;** | Live encoding seconds grouped by output video codec. |  [optional]
**encodingSecondsByResolution** | **kotlin.collections.Map&lt;kotlin.String, kotlin.Long&gt;** | Live encoding seconds grouped by output resolution. |  [optional]



