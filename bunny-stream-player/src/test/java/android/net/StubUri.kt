package android.net

import android.os.Parcel

/**
 * The one concrete [Uri] a plain JVM unit test can build.
 *
 * The unit-test `android.jar` keeps [Uri] abstract with a package-private constructor, and with
 * `isReturnDefaultValues` every factory ([Uri.parse], [Uri.EMPTY]) yields null — while media3's
 * `DataSpec` insists on a non-null uri. Living in `android.net` is what lets this class call the
 * constructor. Nothing here is dereferenced by the code under test; the accessors only exist to
 * satisfy the abstract contract.
 */
internal class StubUri(private val value: String) : Uri() {
    override fun buildUpon(): Uri.Builder = error("not needed by tests")
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = null
    override fun getPath(): String? = null
    override fun getPathSegments(): List<String> = emptyList()
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String? = null
    override fun getSchemeSpecificPart(): String? = null
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun toString(): String = value
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}
