package com.s1nn3rv2.kartka.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import com.s1nn3rv2.kartka.LocalAppName
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed

private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** True when the phone has a fingerprint or a screen lock that can unlock the app. */
fun canLock(context: Context) =
    context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Covers the app until the fingerprint (or the screen lock PIN) is confirmed. The app stays underneath, so open notes
 * and the back stack are kept. If the phone no longer has a screen lock, it unlocks right away instead of locking out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val unlock by rememberUpdatedState(onUnlock)
    val name = LocalAppName.current
    fun prompt() {
        if (!canLock(context)) return unlock()
        BiometricPrompt.Builder(context)
            .setTitle("Unlock $name")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
            .authenticate(CancellationSignal(), context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = unlock()
            })
    }
    // The prompt only shows for an app in the foreground; the lock screen appears while the app is still starting.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { lifecycle.withResumed { prompt() } }
    BackHandler { activity?.moveTaskToBack(true) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            // Swallows every touch, so nothing underneath can be used while locked.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeIcon(Icons.Outlined.Lock, MaterialShapes.Cookie7Sided)
        Text("$name is locked", style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.onBackground)
        Button(onClick = ::prompt, shapes = ButtonDefaults.shapes()) { Text("Unlock") }
    }
}
