// bunny-stream-tv/src/main/java/net/bunny/tv/navigation/TVKeyEventHandler.kt
package net.bunny.tv.navigation

import android.view.KeyEvent

class TVKeyEventHandler(private val onKeyEvent: (KeyEvent) -> Boolean) {
    
    companion object {
        // TV Remote key codes
        const val KEYCODE_MEDIA_PLAY_PAUSE = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        const val KEYCODE_MEDIA_PLAY = KeyEvent.KEYCODE_MEDIA_PLAY
        const val KEYCODE_MEDIA_PAUSE = KeyEvent.KEYCODE_MEDIA_PAUSE
        const val KEYCODE_MEDIA_FAST_FORWARD = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        const val KEYCODE_MEDIA_REWIND = KeyEvent.KEYCODE_MEDIA_REWIND
        const val KEYCODE_MEDIA_NEXT = KeyEvent.KEYCODE_MEDIA_NEXT
        const val KEYCODE_MEDIA_PREVIOUS = KeyEvent.KEYCODE_MEDIA_PREVIOUS
        
        // D-pad keys
        const val KEYCODE_DPAD_CENTER = KeyEvent.KEYCODE_DPAD_CENTER
        const val KEYCODE_DPAD_UP = KeyEvent.KEYCODE_DPAD_UP
        const val KEYCODE_DPAD_DOWN = KeyEvent.KEYCODE_DPAD_DOWN
        const val KEYCODE_DPAD_LEFT = KeyEvent.KEYCODE_DPAD_LEFT
        const val KEYCODE_DPAD_RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT
        
        // Other important keys
        const val KEYCODE_BACK = KeyEvent.KEYCODE_BACK
        const val KEYCODE_MENU = KeyEvent.KEYCODE_MENU
        const val KEYCODE_ENTER = KeyEvent.KEYCODE_ENTER
    }
    
    fun handleKeyEvent(keyEvent: KeyEvent): Boolean {
        return onKeyEvent(keyEvent)
    }
    
    fun isMediaKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KEYCODE_MEDIA_PLAY_PAUSE,
            KEYCODE_MEDIA_PLAY,
            KEYCODE_MEDIA_PAUSE,
            KEYCODE_MEDIA_FAST_FORWARD,
            KEYCODE_MEDIA_REWIND,
            KEYCODE_MEDIA_NEXT,
            KEYCODE_MEDIA_PREVIOUS -> true
            else -> false
        }
    }
    
    fun isDPadKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KEYCODE_DPAD_CENTER,
            KEYCODE_DPAD_UP,
            KEYCODE_DPAD_DOWN,
            KEYCODE_DPAD_LEFT,
            KEYCODE_DPAD_RIGHT -> true
            else -> false
        }
    }
}
