package com.s1nn3rv2.kartka

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.staticCompositionLocalOf
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NoteLayout { LIST, CARDS }

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class TextSize(val label: String, val scale: Float) { SMALL("Small", 0.9f), DEFAULT("Default", 1f), LARGE("Large", 1.15f) }

/** The name on the launcher and inside the app; each has its own launcher entry (activity alias in the manifest). */
enum class AppName(val label: String, val alias: String) { KARTKA("Kartka", ".Kartka"), NOTES("Notes", ".Notes") }

// "Right away" still allows a few seconds, so picking a photo or answering the fingerprint prompt doesn't re-lock.
enum class LockAfter(val label: String, val millis: Long) { NOW("Right away", 5_000), MINUTE("1 minute", 60_000), FIVE("5 minutes", 300_000) }

/** App preferences, each readable as a flow for the UI and written straight to SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    inner class Pref<T>(private val key: String, default: T, parse: (String) -> T) {
        private val flow = MutableStateFlow(prefs.getString(key, null)?.let { runCatching { parse(it) }.getOrNull() } ?: default)
        val state: StateFlow<T> = flow.asStateFlow()
        var value: T
            get() = flow.value
            set(v) {
                prefs.edit().putString(key, v.toString()).apply()
                flow.value = v
            }
    }

    /** How note lists are shown: rows or a grid of cards. */
    val layout = Pref("layout", NoteLayout.LIST, NoteLayout::valueOf)
    val theme = Pref("theme", ThemeMode.SYSTEM, ThemeMode::valueOf)
    /** Material You: colors from the wallpaper instead of Material's standard scheme. */
    val dynamicColor = Pref("dynamicColor", true, String::toBooleanStrict)
    val textSize = Pref("textSize", TextSize.DEFAULT, TextSize::valueOf)
    val appName = Pref("appName", AppName.KARTKA, AppName::valueOf)
    val lock = Pref("lock", false, String::toBooleanStrict)
    val lockAfter = Pref("lockAfter", LockAfter.MINUTE, LockAfter::valueOf)
    val hideInRecents = Pref("hideInRecents", false, String::toBooleanStrict)
}

/** Switches the launcher entry to [name]: exactly one alias is enabled. The app keeps running. */
fun Settings.setAppName(context: Context, name: AppName) {
    appName.value = name
    val pm = context.packageManager
    for (n in AppName.entries) {
        val state = if (n == name) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(ComponentName(context, context.packageName + n.alias), state, PackageManager.DONT_KILL_APP)
    }
}

/** The app name chosen in Settings, for text inside the app. */
val LocalAppName = staticCompositionLocalOf { AppName.KARTKA.label }

/** Locks the app on start and after it was in the background for [Settings.lockAfter]. */
class AppLock(private val settings: Settings) {
    val locked = MutableStateFlow(settings.lock.value)
    private var leftAt = 0L

    fun onLeave() {
        leftAt = SystemClock.elapsedRealtime()
    }

    fun onReturn() {
        if (settings.lock.value && leftAt != 0L && SystemClock.elapsedRealtime() - leftAt >= settings.lockAfter.value.millis) {
            locked.value = true
        }
    }
}
