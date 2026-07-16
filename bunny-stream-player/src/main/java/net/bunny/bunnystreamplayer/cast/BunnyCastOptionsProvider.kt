package net.bunny.bunnystreamplayer.cast

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Points the Cast framework at the Bunny Stream receiver application.
 *
 * The Default Media Receiver (media3's [androidx.media3.cast.DefaultCastOptionsProvider])
 * cannot play Bunny's fMP4/CMAF HLS assets at all — casting them shows only
 * the idle Cast logo on the TV. The Bunny receiver enables Shaka for HLS and
 * additionally understands the Bunny sender contract carried in the load
 * request's customData (Widevine DRM configuration and player theming); see
 * the receiver README in the bunnynet-stream-player repository.
 */
class BunnyCastOptionsProvider : OptionsProvider {

    companion object {
        /** The production Bunny Stream receiver application. */
        const val DEFAULT_RECEIVER_APPLICATION_ID = "0067F7FB"

        /**
         * Apps can point the SDK at a different receiver (e.g. the staging
         * app) with a manifest meta-data entry:
         *
         * ```xml
         * <meta-data
         *     android:name="net.bunny.cast.RECEIVER_APPLICATION_ID"
         *     android:value="E8262297" />
         * ```
         */
        const val RECEIVER_APPLICATION_ID_METADATA = "net.bunny.cast.RECEIVER_APPLICATION_ID"

        private val APP_ID_FORMAT = Regex("^[0-9A-F]{8}$", RegexOption.IGNORE_CASE)

        internal fun receiverApplicationId(context: Context): String {
            val override = try {
                context.packageManager
                    .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    .metaData?.getString(RECEIVER_APPLICATION_ID_METADATA)?.trim()
            } catch (_: Exception) {
                null
            }
            return if (override != null && APP_ID_FORMAT.matches(override)) {
                override
            } else {
                DEFAULT_RECEIVER_APPLICATION_ID
            }
        }
    }

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(receiverApplicationId(context))
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
