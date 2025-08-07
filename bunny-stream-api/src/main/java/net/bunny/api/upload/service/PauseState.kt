package net.bunny.api.upload.service

sealed class PauseState {
    object Unsupported : PauseState()

    object Paused : PauseState()

    object Uploading : PauseState()
}