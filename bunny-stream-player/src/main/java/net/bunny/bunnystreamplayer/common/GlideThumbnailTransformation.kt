package net.bunny.bunnystreamplayer.common

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.security.MessageDigest

class GlideThumbnailTransformation(position: Long, thumbnailsEach: Int) : BitmapTransformation() {

    private val x: Int = ((position / thumbnailsEach).toInt() % MAX_COLUMNS)
    private val y: Int = ((position / thumbnailsEach).toInt() / MAX_LINES)

    override fun transform(
        pool: BitmapPool, toTransform: Bitmap,
        outWidth: Int, outHeight: Int
    ): Bitmap {
        val width = toTransform.getWidth() / MAX_COLUMNS
        val height = toTransform.getHeight() / MAX_LINES
        return Bitmap.createBitmap(toTransform, x * width, y * height, width, height)
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        val data = ByteBuffer.allocate(8).putInt(x).putInt(y).array()
        messageDigest.update(data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as GlideThumbnailTransformation
        return if (x != other.x) false else y == other.y
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        return result
    }

    companion object {
        private const val MAX_LINES = 6
        private const val MAX_COLUMNS = 6
    }
}
