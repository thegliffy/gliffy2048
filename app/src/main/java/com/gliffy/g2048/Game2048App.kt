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

    /** Set by the live [com.gliffy.g2048.ui.GameStateMachine] so the
     *  activity can flush the in-progress game on onStop(). */
    var liveMachine: Any? = null

    /** Ask the live game machine to persist its state (no-op if none). */
    fun flushLiveState() {
        (liveMachine as? com.gliffy.g2048.ui.GameMachine)?.onPauseSave()
    }

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
