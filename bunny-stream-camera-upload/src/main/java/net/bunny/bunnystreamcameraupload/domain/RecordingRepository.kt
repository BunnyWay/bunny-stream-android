package net.bunny.bunnystreamcameraupload.domain

import arrow.core.Either

interface RecordingRepository {
    suspend fun prepareRecording(libraryId: Long): Either<String, String>
}