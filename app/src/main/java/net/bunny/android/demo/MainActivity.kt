package net.bunny.android.demo

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import net.bunny.android.demo.ui.App
import net.bunny.android.demo.ui.theme.BunnyStreamTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BunnyStreamTheme {
                App()
            }
        }
    }
}