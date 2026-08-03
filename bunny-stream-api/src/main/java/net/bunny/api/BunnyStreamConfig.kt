package net.bunny.api

/**
 * Everything one SDK instance needs to talk to one Bunny Stream library.
 *
 * Each instance created from a config authenticates, resolves and uploads on its own. Two configs
 * with different keys can be live at the same time without interfering — see
 * [BunnyStreamApi.create].
 *
 * @param accessKey the library's API key, from the Bunny dashboard under Stream > your library >
 *   API. Every call the SDK makes authenticates with it.
 * @param libraryId the library this instance addresses. Calls that take a `libraryId` argument
 *   still let you override it per call; this is the default the SDK's own views use.
 * @param baseApi the Stream API host. Leave it alone unless Bunny has given you a different one.
 */
data class BunnyStreamConfig(
    val accessKey: String,
    val libraryId: Long,
    val baseApi: String = BuildConfig.BASE_API,
) {
    init {
        require(accessKey.isNotBlank()) { "accessKey must not be blank" }
        require(libraryId > 0) { "libraryId must be a positive library id, was $libraryId" }
        require(baseApi.isNotBlank()) { "baseApi must not be blank" }
    }

    /**
     * Renders the config without the key.
     *
     * A data class prints every property, and configs end up in log lines and crash reports. Only
     * enough of the key survives to tell two of them apart.
     */
    override fun toString(): String =
        "BunnyStreamConfig(accessKey=${accessKey.take(KEPT_KEY_CHARS)}…, " +
            "libraryId=$libraryId, baseApi=$baseApi)"

    private companion object {
        /** How much of the key survives rendering — enough to tell two of them apart, no more. */
        const val KEPT_KEY_CHARS = 4
    }
}
