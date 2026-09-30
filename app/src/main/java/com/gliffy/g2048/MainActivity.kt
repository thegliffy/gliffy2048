package com.gliffy.g2048

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gliffy.g2048.ui.GameScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GameScreen() }
    }

    /** Persist the in-progress game when the app leaves the foreground.
     *  (Moves already save on every move; this covers the rare cases where
     *  a move's save raced a background transition.) */
    override fun onStop() {
        super.onStop()
        Game2048App.instance.flushLiveState()
    }
}
