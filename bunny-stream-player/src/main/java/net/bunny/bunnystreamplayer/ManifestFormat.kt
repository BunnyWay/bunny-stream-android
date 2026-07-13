package net.bunny.bunnystreamplayer

/**
 * Streaming manifest container, resolved from the playback URL so the player can serve **HLS or
 * DASH** live/VOD from the same engine (ExoPlayer supports both; AVPlayer on iOS is HLS-only, hence
 * DASH is Android-only). Drives the media3 MediaItem MIME type and the CMCD `sf` (streaming format)
 * field.
 *
 * @property cmcdSf the CMCD (CTA-5004) `sf` code — `h` for HLS, `d` for DASH.
 */
internal enum class ManifestFormat(val cmcdSf: String) {
    HLS("h"),
    DASH("d");

    internal companion object {
        /**
         * Classifies [url] by its manifest extension. A `.mpd` path is DASH; **everything else —
         * including `.m3u8` and Bunny's extension-less live/fallback URLs — defaults to HLS**, so
         * every current Bunny URL keeps its exact HLS behaviour and only an explicit DASH manifest
         * switches the engine. Query/fragment are stripped before matching.
         */
        fun fromUrl(url: String?): ManifestFormat {
            val path = url
                ?.substringBefore('?')
                ?.substringBefore('#')
                ?.lowercase()
                .orEmpty()
            return if (path.endsWith(".mpd")) DASH else HLS
        }
    }
}
