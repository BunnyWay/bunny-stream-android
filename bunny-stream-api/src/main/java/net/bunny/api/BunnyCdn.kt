package net.bunny.api

/**
 * Constants for Bunny's video CDN, shared across the SDK modules (and reusable by consumer apps).
 */
object BunnyCdn {
    /**
     * `Referer` header sent on Bunny CDN requests (playback, the `/play` API, thumbnails, posters
     * and the pre-stream trailer).
     *
     * Libraries with **"Block direct url file access"** (hotlink protection) enabled reject any
     * request whose `Referer` isn't on the allowed list with HTTP 403. This value is Bunny's
     * official iframe embed origin (`iframe.mediadelivery.net`), which such libraries allow by
     * default — it's what the web embed player itself sends.
     *
     * Exposed publicly so apps that load Bunny thumbnails through their own image loader (e.g. Coil
     * or Glide) can send the same header and keep working with the block on.
     */
    const val REFERER: String = "https://iframe.mediadelivery.net/"
}
