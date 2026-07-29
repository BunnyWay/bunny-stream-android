# Bunny's RTMP ingest carries the credential in the stream name. RootEncoder logs every RTMP
# command it sends verbatim at Log.i, and offers no way to disable that through its API
# (setLogs() only covers the media sender, not the command manager).
#
# Removes Android log calls up to INFO from RootEncoder's own classes only. Nothing the
# integrator wrote is affected, and warnings and errors from the library still come through.
-maximumremovedandroidloglevel 4 class com.pedro.** { *; }
