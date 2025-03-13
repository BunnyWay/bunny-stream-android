package net.bunnystream.android.demo.settings

fun String.toLongOrDefault(defaultValue: Long): Long {
    return try {
        this.toLong()
    } catch (e: Exception) {
        return defaultValue
    }
}