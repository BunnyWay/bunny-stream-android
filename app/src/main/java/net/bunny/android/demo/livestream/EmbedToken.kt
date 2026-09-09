package net.bunny.android.demo.livestream

import java.security.MessageDigest

/**
 * Generates Bunny Stream embed / play-data tokens for token-authenticated libraries.
 *
 * Per Bunny's spec (Embedded view token authentication):
 *   token   = SHA256_HEX(tokenAuthKey + videoId + expires)
 *   expires = UNIX timestamp in **seconds**
 *
 * For a live stream, `videoId` is the stream GUID.
 *
 * DEBUG / demo helper only. Bunny's docs are explicit that the token security key must never be
 * embedded in a client app — generate tokens server-side in production. This exists so the sample
 * app can play token-authenticated streams without standing up a backend.
 */
internal object EmbedToken {

    fun generate(tokenAuthKey: String, videoId: String, expiresEpochSeconds: Long): String {
        val raw = tokenAuthKey + videoId + expiresEpochSeconds
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
