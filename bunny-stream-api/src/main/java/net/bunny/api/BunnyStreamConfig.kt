package net.bunny.api

/**
 * Optional wrapper/integrator identifier that gets appended to the SDK's native User-Agent.
 *
 * @param name the integrator name, e.g. "bunny-stream-react-native".
 * @param version the integrator version, e.g. "0.1.1".
 */
public data class BunnyStreamIntegrator(
    val name: String,
    val version: String,
) {
    init {
        require(name.isNotBlank() && name.none { it.isWhitespace() || it.isISOControl() }) {
            "integrator name must be non-blank and must not contain whitespace or control characters"
        }
        require(version.isNotBlank() && version.none { it.isWhitespace() || it.isISOControl() }) {
            "integrator version must be non-blank and must not contain whitespace or control characters"
        }
    }

    override fun toString(): String = "$name/$version"
}

/**
 * Everything one SDK instance needs to talk to one Bunny Stream library.
 *
 * Each instance created from a config authenticates, resolves and uploads on its own. Two configs
 * with different keys can be live at the same time without interfering — see
 * [BunnyStreamApi.create].
 *
 * **Playback-only apps need no key.** Playing a video or a live stream goes through Bunny's public
 * `/play` endpoints, which take the library id, the video or stream id and — on a library with
 * token authentication — a signed token. Leave [accessKey] out and the instance plays everything
 * the library serves publicly; the management calls (listing, creating, editing, uploading,
 * broadcasting) then fail with a terminal [net.bunny.api.error.BunnyError.InvalidState] without
 * reaching the network. Ship the key only in an app that manages the library: anyone who can
 * unpack the APK can read it.
 *
 * @param accessKey the library's API key, from the Bunny dashboard under Stream > your library >
 *   API, or `null` for a playback-only instance. When set, every call the SDK makes authenticates
 *   with it.
 * @param libraryId the library this instance addresses. Calls that take a `libraryId` argument
 *   still let you override it per call; this is the default the SDK's own views use.
 * @param baseApi the Stream API host. Leave it alone unless Bunny has given you a different one.
 * @param integrator optional identifier of the framework wrapping the SDK. When set, it is appended
 *   to the SDK's User-Agent header as `bunny-stream-android/<version> <name>/<version>`.
 */
data class BunnyStreamConfig(
    val accessKey: String? = null,
    val libraryId: Long,
    val baseApi: String = BuildConfig.BASE_API,
    val integrator: BunnyStreamIntegrator? = null,
) {
    init {
        // Null is the deliberate "playback only" choice. A blank string is not: it is almost
        // always a key that failed to load, and treating it as "no key" would turn that bug into
        // management calls failing much later with a less obvious message.
        require(accessKey == null || accessKey.isNotBlank()) {
            "accessKey must not be blank. Pass null (or leave it out) for a playback-only instance."
        }
        // A key pasted from a dashboard or read from a properties file often carries a trailing
        // space or newline. It travels into the AccessKey header verbatim, so the request fails
        // with 401 and the key still *looks* right wherever you print it. Say so here instead.
        require(accessKey == null || accessKey == accessKey.trim()) {
            "accessKey has leading or trailing whitespace — it would be sent as-is and rejected " +
                "with 401. Trim it before passing it in."
        }
        require(libraryId > 0) { "libraryId must be a positive library id, was $libraryId" }
        require(baseApi.isNotBlank()) { "baseApi must not be blank" }
    }

    /**
     * True when this config carries no [accessKey]: the instance can play, but every management
     * call fails with [net.bunny.api.error.BunnyError.InvalidState] before reaching the network.
     */
    val isPlaybackOnly: Boolean
        get() = accessKey == null

    /** Full User-Agent string used by this SDK instance, including any configured integrator. */
    val userAgent: String
        get() = BuildConfig.USER_AGENT + (integrator?.let { " $it" } ?: "")

    /**
     * Renders the config without the key.
     *
     * A data class prints every property, and configs end up in log lines and crash reports. Only
     * enough of the key survives to tell two of them apart.
     */
    override fun toString(): String =
        "BunnyStreamConfig(accessKey=${accessKey?.let { "${it.take(KEPT_KEY_CHARS)}…" }}, " +
            "libraryId=$libraryId, baseApi=$baseApi, integrator=$integrator)"

    private companion object {
        /** How much of the key survives rendering — enough to tell two of them apart, no more. */
        const val KEPT_KEY_CHARS = 4
    }
}
