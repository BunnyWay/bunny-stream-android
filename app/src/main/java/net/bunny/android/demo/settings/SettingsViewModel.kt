package net.bunny.android.demo.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.bunny.android.demo.App
import net.bunny.api.BunnyStreamApi
import net.bunny.api.BunnyStreamConfig
import net.bunny.android.demo.livestream.EmbedToken
import net.bunny.api.StreamApi
import net.bunny.api.error.BunnyError
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.errorOrNull
import net.bunny.api.error.fold

/** What the settings screen is doing, and what it has to say about it. */
sealed interface SettingsState {
    data object Idle : SettingsState

    /** A verification call is in flight. */
    data object Checking : SettingsState

    /** The credentials work — the screen can close. */
    data object Verified : SettingsState

    /** The credentials were rejected; [message] explains what to fix. */
    data class Failed(val message: String) : SettingsState
}

/** The probe token only has to outlive one API call. */
private const val PROBE_TOKEN_TTL_SECONDS = 300L

class SettingsViewModel : ViewModel() {

    private val prefs = App.di.localPrefs

    var accessKey by mutableStateOf(prefs.accessKey)
        private set

    var libraryId by mutableLongStateOf(prefs.libraryId)
        private set

    var tokenAuthKey by mutableStateOf(prefs.tokenAuthKey)
        private set

    var state by mutableStateOf<SettingsState>(SettingsState.Idle)
        private set

    fun dismissError() {
        state = SettingsState.Idle
    }

    /**
     * Saves the credentials only after proving they work.
     *
     * The screen used to accept anything and close, so a mistyped library, the account API key
     * instead of the library one, or a key pasted with a trailing space all looked like success
     * and only surfaced later as "could not load the video library" on a different screen. One
     * cheap listing call turns that into an answer here, where it can still be corrected.
     */
    fun saveAndVerify(rawAccessKey: String, rawLibraryId: String, rawTokenAuthKey: String) {
        val key = rawAccessKey.trim()
        val library = rawLibraryId.trim().toLongOrDefault(-1)
        val tokenKey = rawTokenAuthKey.trim()

        if (key.isEmpty()) {
            state = SettingsState.Failed("Enter the library's API key.")
            return
        }
        if (library <= 0) {
            state = SettingsState.Failed("Enter the numeric library id, as shown in the Bunny dashboard.")
            return
        }

        state = SettingsState.Checking
        viewModelScope.launch {
            // Verify on a throwaway instance so a bad key never replaces a working setup.
            val probe = BunnyStreamApi.create(
                App.di.context,
                BunnyStreamConfig(accessKey = key, libraryId = library),
            )
            val result = probe.videoRepository.listVideos(library, page = 1, itemsPerPage = 1)

            val failure = when (result) {
                is BunnyResult.Err -> explain(result.error, library)
                is BunnyResult.Ok -> {
                    // The key is good. Now check whether the library needs a playback token: with
                    // token authentication on, listing still succeeds but play data answers 401.
                    // Saving without the key here would leave every player unable to load a thing.
                    val sample = result.value.items.firstOrNull()?.id
                    if (sample == null) null else tokenProblem(probe, library, sample, tokenKey)
                }
            }
            probe.release()

            if (failure != null) {
                state = SettingsState.Failed(failure)
            } else {
                accessKey = key
                libraryId = library
                tokenAuthKey = tokenKey
                App.di.localPrefs.tokenAuthKey = tokenKey
                App.di.updateKeys(key, library)
                state = SettingsState.Verified
            }
        }
    }

    /**
     * Checks the token authentication key against the library, using the same signed-token path
     * playback uses. Returns null when there is nothing to fix.
     *
     * Play data is the endpoint that enforces it: with token authentication on it answers 401
     * without a valid token, while listing videos keeps working. So an empty key on a protected
     * library, and a wrong key on one, are both caught here rather than becoming a player that
     * loads nothing.
     */
    private suspend fun tokenProblem(
        probe: StreamApi,
        library: Long,
        videoId: String,
        tokenKey: String,
    ): String? {
        val expires = System.currentTimeMillis() / 1000 + PROBE_TOKEN_TTL_SECONDS
        val token = tokenKey.takeIf { it.isNotEmpty() }
            ?.let { EmbedToken.generate(it, videoId, expires) }

        val playData = probe.videoRepository.fetchVideoPlayData(
            libraryId = library,
            videoId = videoId,
            token = token,
            expires = token?.let { expires },
        )
        val error = playData.errorOrNull()
        return when {
            error !is BunnyError.Auth -> null

            tokenKey.isEmpty() ->
                "Library $library has token authentication enabled, so playback needs its token " +
                    "authentication key. Add it above — Bunny dashboard > Stream > your library > " +
                    "Security."

            else ->
                "The token authentication key was rejected by library $library. Check it against " +
                    "Bunny dashboard > Stream > your library > Security."
        }
    }

    /**
     * Turns the failure into the thing the user has to change. The API answers the same 401 for
     * "wrong key", "key from another library" and "account key instead of library key", so the
     * message names all three rather than guessing.
     */
    private fun explain(error: BunnyError, library: Long): String = when (error) {
        is BunnyError.Auth ->
            "The key was rejected for library $library. Use the library's own API key " +
                "(Bunny dashboard > Stream > your library > API), not an account-wide key, and " +
                "check the key belongs to this library."

        is BunnyError.NotFound ->
            "No library $library on this account. Check the id in the Bunny dashboard."

        is BunnyError.Network ->
            "Could not reach Bunny. Check the connection and try again."

        else -> error.message
    }
}
