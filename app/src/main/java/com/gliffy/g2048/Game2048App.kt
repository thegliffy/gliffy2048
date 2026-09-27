package com.gliffy.g2048

import android.app.Application
import android.content.Context

class Game2048App : Application() {

    lateinit var haptics: HapticsController
        private set
    lateinit var audio: AudioController
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        haptics = HapticsController(this)
        audio = AudioController(this)
        prefs = Prefs(this)
    }

    companion object {
        @Volatile lateinit var instance: Game2048App
            private set
    }
}
