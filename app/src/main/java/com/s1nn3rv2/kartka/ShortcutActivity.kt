package com.s1nn3rv2.kartka

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Launcher shortcuts land here and are passed on to [MainActivity] with a one-time [EXTRA_REQUEST] id. Android keeps
 * the intent that started a task and hands it to the app again after an update or a force stop; the id lets
 * [MainActivity] tell such a replay from a real tap, so it never creates a second note.
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(intent.action)
                .putExtra(EXTRA_REQUEST, System.currentTimeMillis())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        const val EXTRA_REQUEST = "com.s1nn3rv2.kartka.REQUEST"
    }
}
