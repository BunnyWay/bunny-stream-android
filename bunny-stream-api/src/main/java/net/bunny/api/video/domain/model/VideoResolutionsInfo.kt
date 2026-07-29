package net.bunny.api.video.domain.model

/**
 * What renditions exist for a video and where they live — the diagnostic behind "why is 1080p
 * missing" and the input to deleting renditions you no longer want to pay for.
 *
 * @property availableResolutions renditions that actually exist and can be played.
 * @property configuredResolutions renditions the library is set up to produce; anything here but
 *   not in [availableResolutions] either failed or is still transcoding.
 * @property playlistResolutions renditions referenced by the HLS playlist.
 * @property storageResolutions renditions present in storage.
 * @property mp4Resolutions renditions available as MP4 fallback files.
 * @property storageObjects raw storage entries backing the renditions.
 * @property oldResolutions entries left over from the pre-rename storage layout.
 * @property hasBothOldAndNewResolutionFormat the video straddles both layouts — deleting
 *   renditions needs care, since the same rendition may exist twice.
 */
public data class VideoResolutionsInfo(
    val videoId: String,
    val videoLibraryId: Long,
    val availableResolutions: List<String>,
    val configuredResolutions: List<String>,
    val playlistResolutions: List<ResolutionReference>,
    val storageResolutions: List<ResolutionReference>,
    val mp4Resolutions: List<ResolutionReference>,
    val storageObjects: List<StorageObject>,
    val oldResolutions: List<StorageObject>,
    val hasBothOldAndNewResolutionFormat: Boolean,
    val hasOriginal: Boolean,
)

/** A rendition and the storage path it is served from. */
public data class ResolutionReference(
    val resolution: String?,
    val path: String?,
)

/**
 * One file in the library's storage zone.
 *
 * @property storageZoneId numeric zone id — the Bunny Storage API keys on this, not on
 *   [storageZoneName].
 * @property checksum lets a caller verify a stored rendition's integrity.
 * @property replicatedZones which geo-replicas hold a copy.
 * @property serverId which storage server holds the object; useful when debugging edge behaviour.
 */
public data class StorageObject(
    val id: String?,
    val storageZoneName: String?,
    val storageZoneId: Long?,
    val path: String?,
    val objectName: String?,
    val lengthBytes: Long,
    val dateCreated: String?,
    val lastChanged: String?,
    val isDirectory: Boolean,
    val contentType: String?,
    val serverId: Int?,
    val userId: String?,
    val checksum: String?,
    val replicatedZones: String?,
)
