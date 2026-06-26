package net.bunny.android.demo.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bunny.android.demo.App
import net.bunny.api.BunnyStreamApi
import net.bunny.api.livestream.domain.model.LibraryWatermarkSettings
import java.io.File

class SettingsViewModel : ViewModel() {

    /** State of the library-level watermark upload/remove action shown in the editor. */
    sealed interface WatermarkState {
        /** Nothing in flight. */
        data object Idle : WatermarkState

        /** An upload or removal is running. */
        data object Working : WatermarkState

        /** The last action finished: [message] describes the outcome; [isError] picks the styling. */
        data class Result(val message: String, val isError: Boolean) : WatermarkState
    }

    private val prefs = App.di.localPrefs

    private val repository
        get() = App.di.streamSdk.liveStreamRepository

    var accessKey by mutableStateOf(prefs.accessKey)
        private set

    var libraryId by mutableLongStateOf(prefs.libraryId)
        private set

    /** Account-level API key (Account Settings → API) — required for the library watermark. */
    var accountApiKey by mutableStateOf(prefs.accountApiKey)
        private set

    var watermarkState by mutableStateOf<WatermarkState>(WatermarkState.Idle)
        private set

    // Bunny exposes no API to read back the stored watermark image, so we cache the last image this
    // app uploaded and preview that across sessions. (Reflects what was set here, not necessarily a
    // watermark set elsewhere, e.g. via the dashboard.)
    private val watermarkCacheFile: File
        get() = File(App.di.context.filesDir, "last_watermark.img")

    var cachedWatermarkUri by mutableStateOf<Uri?>(
        watermarkCacheFile.takeIf { it.exists() }?.let { Uri.fromFile(it) },
    )
        private set

    /** Current library watermark placement (position/size in %), loaded via [loadWatermarkSettings]. */
    var watermarkPlacement by mutableStateOf<LibraryWatermarkSettings?>(null)
        private set

    fun updateKeys(accessKey: String, libraryId: Long, accountApiKey: String) {
        this.accessKey = accessKey
        this.libraryId = libraryId
        this.accountApiKey = accountApiKey
        // Persist the account key (used only for Core Platform calls like the watermark); the
        // Stream key + library id are applied to the SDK via Di.updateKeys.
        prefs.accountApiKey = accountApiKey
        App.di.updateKeys(accessKey, libraryId)
    }

    /**
     * Uploads [imageBytes] as the library watermark. The watermark applies to the **whole library**
     * (every live stream and video), so it targets the currently active library — save settings
     * first if the library ID was just changed.
     */
    fun uploadWatermark(imageBytes: ByteArray, contentType: String) {
        val library = BunnyStreamApi.libraryId
        if (library <= 0L) {
            watermarkState = WatermarkState.Result("Set a valid library ID first", isError = true)
            return
        }
        val accountKey = prefs.accountApiKey
        if (accountKey.isBlank()) {
            watermarkState = WatermarkState.Result(
                "Watermark needs your account API key (Account Settings → API)",
                isError = true,
            )
            return
        }
        watermarkState = WatermarkState.Working
        viewModelScope.launch {
            repository.setLibraryWatermark(library, imageBytes, contentType, apiKey = accountKey).fold(
                ifLeft = { watermarkState = WatermarkState.Result(it, isError = true) },
                ifRight = {
                    // Cache the uploaded image so it can be previewed in future sessions.
                    withContext(Dispatchers.IO) {
                        runCatching { watermarkCacheFile.writeBytes(imageBytes) }
                    }
                    cachedWatermarkUri = Uri.fromFile(watermarkCacheFile)
                    watermarkState = WatermarkState.Result("Watermark uploaded", isError = false)
                },
            )
        }
    }

    /** Removes the library watermark. */
    fun removeWatermark() {
        val library = BunnyStreamApi.libraryId
        if (library <= 0L) {
            watermarkState = WatermarkState.Result("Set a valid library ID first", isError = true)
            return
        }
        val accountKey = prefs.accountApiKey
        if (accountKey.isBlank()) {
            watermarkState = WatermarkState.Result(
                "Watermark needs your account API key (Account Settings → API)",
                isError = true,
            )
            return
        }
        watermarkState = WatermarkState.Working
        viewModelScope.launch {
            repository.deleteLibraryWatermark(library, apiKey = accountKey).fold(
                ifLeft = { watermarkState = WatermarkState.Result(it, isError = true) },
                ifRight = {
                    withContext(Dispatchers.IO) { runCatching { watermarkCacheFile.delete() } }
                    cachedWatermarkUri = null
                    watermarkState = WatermarkState.Result("Watermark removed", isError = false)
                },
            )
        }
    }

    /** Loads the current watermark placement so the size/position fields can be prefilled. */
    fun loadWatermarkSettings() {
        val library = BunnyStreamApi.libraryId
        val accountKey = prefs.accountApiKey
        if (library <= 0L || accountKey.isBlank()) return
        viewModelScope.launch {
            repository.getLibraryWatermarkSettings(library, apiKey = accountKey).fold(
                // Non-fatal: leave the fields at their defaults if we can't read the library.
                ifLeft = { /* ignore */ },
                ifRight = { watermarkPlacement = it },
            )
        }
    }

    /** Saves the watermark position/size (all %) to the library. */
    fun saveWatermarkSettings(positionLeft: Int, positionTop: Int, width: Int, height: Int) {
        val library = BunnyStreamApi.libraryId
        if (library <= 0L) {
            watermarkState = WatermarkState.Result("Set a valid library ID first", isError = true)
            return
        }
        val accountKey = prefs.accountApiKey
        if (accountKey.isBlank()) {
            watermarkState = WatermarkState.Result(
                "Watermark needs your account API key (Account Settings → API)",
                isError = true,
            )
            return
        }
        watermarkState = WatermarkState.Working
        viewModelScope.launch {
            repository.updateLibraryWatermarkSettings(
                libraryId = library,
                positionLeft = positionLeft,
                positionTop = positionTop,
                width = width,
                height = height,
                apiKey = accountKey,
            ).fold(
                ifLeft = { watermarkState = WatermarkState.Result(it, isError = true) },
                ifRight = {
                    watermarkPlacement = LibraryWatermarkSettings(
                        hasWatermark = watermarkPlacement?.hasWatermark ?: true,
                        positionLeft = positionLeft,
                        positionTop = positionTop,
                        width = width,
                        height = height,
                    )
                    watermarkState = WatermarkState.Result("Watermark position saved", isError = false)
                },
            )
        }
    }
}
