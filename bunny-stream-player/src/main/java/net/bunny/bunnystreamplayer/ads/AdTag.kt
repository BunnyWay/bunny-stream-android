package net.bunny.bunnystreamplayer.ads

/**
 * Returns [raw] as an ad tag (VAST or VMAP) URL the IMA SDK can request, or `null` when there is
 * nothing to request.
 *
 * The dashboard field is free text, so a blank value, stray whitespace or a non-HTTP value has to
 * mean "no ads" rather than an ad request that is bound to fail. A failing request does not block
 * playback (content starts once IMA gives up), but it does hold the first frame back until IMA's
 * load timeout runs out.
 */
internal fun adTagUrlOrNull(raw: String?): String? {
    val tag = raw?.trim().orEmpty()
    if (tag.isEmpty()) return null
    val scheme = tag.substringBefore("://", missingDelimiterValue = "").lowercase()
    return tag.takeIf { scheme == "http" || scheme == "https" }
}

/**
 * The ad tag to attach when the live surface (re)loads [content], or `null` for a load without ads.
 *
 * The live surface reloads whenever the play data changes or a playback failure is recovered, and
 * every load builds a fresh ads loader. Attaching the tag to each of those loads would play the
 * pre-roll again on every refresh, so a piece of content gets its ads once. The live edge and the
 * ended stream's recording are separate content, and the recording gets its own pre-roll.
 *
 * Content is named by the caller rather than by its URL: a playlist URL can change between
 * refreshes (a fresh token, the list's URL giving way to the play data's) while the viewer keeps
 * watching the same thing.
 *
 * @param content what is about to be loaded, for example the stream id for the live edge.
 * @param adTagUrl the stream's ad tag from the live `/play` response, if any.
 * @param adsLoadedFor the content that last started playing with ads on it, or `null` if none did.
 *   The caller only records it once playback starts: a load can be replaced before it ever runs.
 */
internal fun liveAdTagForLoad(content: String, adTagUrl: String?, adsLoadedFor: String?): String? {
    val tag = adTagUrlOrNull(adTagUrl) ?: return null
    return tag.takeIf { content != adsLoadedFor }
}
