package net.bunny.bunnystreamplayer.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.res.use
import net.bunny.player.R

class ToggleableImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    enum class State {
        STATE_DEFAULT, STATE_TOGGLED
    }

    private var defaultIcon: Int = 0
    private var toggledIcon: Int = 0

    var state: State = State.STATE_DEFAULT
        set(value) {
            field = value
            updateStateIcons()
        }

    var tintColor: Int = 0
        set(value) {
            field = value
            if(value != 0) {
                imageTintList = ColorStateList.valueOf(tintColor)
            } else {
                imageTintList = null
            }
        }

    init {
        extractAttrs(attrs)
        if(defaultIcon != 0 && toggledIcon != 0) {
            updateStateIcons()
        }
    }

    private fun updateStateIcons(){
        val icon = when(state) {
            State.STATE_DEFAULT -> defaultIcon
            State.STATE_TOGGLED -> toggledIcon
        }
        setImageResource(icon)
    }

    fun setStateIcons(@DrawableRes state0Icon: Int, @DrawableRes state1Icon: Int){
        this.defaultIcon = state0Icon
        this.toggledIcon = state1Icon
        updateStateIcons()
    }

    private fun extractAttrs(attrs: AttributeSet?) {
        if (attrs != null) {
            context.obtainStyledAttributes(attrs, R.styleable.ToggleableImageButton).use {
                defaultIcon = it.getResourceId(R.styleable.ToggleableImageButton_tibDefaultIcon, 0)
                toggledIcon = it.getResourceId(R.styleable.ToggleableImageButton_tibToggledIcon, 0)
            }
        }
    }
}