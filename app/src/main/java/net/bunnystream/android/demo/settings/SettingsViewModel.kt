package net.bunnystream.android.demo.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import net.bunny.android.demo.App

class SettingsViewModel : ViewModel() {

    private val prefs = App.di.localPrefs

    var accessKey by mutableStateOf(prefs.accessKey)
        private set

    var libraryId by mutableLongStateOf(prefs.libraryId)
        private set

    fun updateKeys(accessKey: String, libraryId: Long){
        this.accessKey = accessKey
        this.libraryId = libraryId
        App.di.updateKeys(accessKey, libraryId)
    }
}