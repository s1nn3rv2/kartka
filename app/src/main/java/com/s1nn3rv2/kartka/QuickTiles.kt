package com.s1nn3rv2.kartka

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tiles that start a note, like the launcher shortcuts (and through the same ShortcutActivity, so a
 * replayed request never creates a second note). On a locked phone, Android asks to unlock first.
 */
abstract class CaptureTile(private val action: String) : TileService() {
    override fun onStartListening() {
        qsTile?.run {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, ShortcutActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun(open) else open()
    }
}

class NewNoteTile : CaptureTile(ACTION_NEW_NOTE)
class ScratchTile : CaptureTile(ACTION_NEW_SCRATCH)
