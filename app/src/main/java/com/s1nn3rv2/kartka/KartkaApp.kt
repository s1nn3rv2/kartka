package com.s1nn3rv2.kartka

import android.app.Application
import kotlinx.coroutines.MainScope

class KartkaApp : Application() {
    val notes by lazy { Notes(this) }
    val settings by lazy { Settings(this) }
    val lock by lazy { AppLock(settings) }

    /** Outlives screens, so saves started while leaving a note still finish. */
    val scope = MainScope()
}
