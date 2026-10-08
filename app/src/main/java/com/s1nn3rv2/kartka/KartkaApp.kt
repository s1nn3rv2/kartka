package com.s1nn3rv2.kartka

import android.app.Application

class KartkaApp : Application() {
    val notes by lazy { Notes(this) }
}
