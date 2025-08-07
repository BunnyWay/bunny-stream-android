package net.bunny.api.settings

import android.graphics.Color
import android.net.Uri

fun String.toColorOrDefault(defaultColor: Int?): Int? {
    return try {
        Color.parseColor(this)
    } catch (e: Exception) {
        defaultColor
    }
}

fun String?.toUri(): Uri? {
    this ?: return null
    return try {
        Uri.parse(this)
    } catch (e: Exception) {
        null
    }
}

fun String.capitalizeWords(): String {
    return this.split(" ").joinToString(" ") {
        it.replaceFirstChar { char ->
            char.uppercaseChar()
        }
    }
}