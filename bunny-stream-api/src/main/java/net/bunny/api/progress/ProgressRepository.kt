package net.bunny.api.progress

import arrow.core.Either

interface ProgressRepository {
    suspend fun saveProgress(libraryId: Long, videoId: String, position: Long): Either<String, Unit>
    suspend fun getProgress(libraryId: Long, videoId: String): Either<String, Long>
    suspend fun clearProgress(libraryId: Long, videoId: String): Either<String, Unit>
}

