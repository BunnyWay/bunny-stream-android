
# LiveStreamStatusModel

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**readyToStart** | **kotlin.Boolean** | Determines if the stream is ready to start based on live status |  [optional]
**primaryLive** | **kotlin.Boolean** | Determines if the primary ingest is live |  [optional]
**backupLive** | **kotlin.Boolean** | Determines if the backup ingest is live |  [optional]
**lastPingAgo** | **kotlin.Long** | The number of milliseconds since the last ping, if available |  [optional]
**duration** | **kotlin.Int** | The stream duration in seconds |  [optional]
**statusTimeUtc** | **kotlin.String** | The UTC time when the status is read |  [optional]



