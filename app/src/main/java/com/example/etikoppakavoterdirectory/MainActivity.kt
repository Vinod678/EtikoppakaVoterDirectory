package com.example.etikoppakavoterdirectory

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.etikoppakavoterdirectory.auth.AuthState
import com.example.etikoppakavoterdirectory.auth.LockReason
import com.example.etikoppakavoterdirectory.ui.AppRoot
import com.example.etikoppakavoterdirectory.ui.auth.LockScreen
import com.example.etikoppakavoterdirectory.ui.theme.EtikoppakaVoterDirectoryTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Screenshots, screen recording and recent-app previews are allowed (app 3.1). Voter data is
        // still never composed until the password has been accepted (see the AuthState switch below).
        enableEdgeToEdge()
        // Privacy: keep voter data, notes and passwords away from autofill / password-manager
        // services (which may be cloud-backed). Applies to every field in the app.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        val auth = (application as VoterDirectoryApp).auth
        // A fresh open of the app (not a rotation / configuration change) always asks for the password.
        if (savedInstanceState == null) auth.lock(LockReason.START)
        // Coming back to the app re-checks the 3-hour limit and the date before anything is shown.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = auth.check()
            override fun onResume(owner: LifecycleOwner) = auth.check()
        })

        setContent {
            EtikoppakaVoterDirectoryTheme {
                val state by auth.state.collectAsState()
                // Screen state (open ward, voter, household, searches, scroll positions) belongs to
                // one sign-in: every sign-in starts on the home screen with nothing filled in.
                val saveable = rememberSaveableStateHolder()
                var lastSession by rememberSaveable { mutableStateOf<String?>(null) }
                when (val s = state) {
                    is AuthState.Locked -> {
                        // Signed out / session ended: forget that sign-in's screens (never saved data).
                        // The ViewModels' searches and filters are reset when the next session starts.
                        LaunchedEffect(Unit) {
                            lastSession?.let(saveable::removeState)
                            lastSession = null
                        }
                        LockScreen(auth, s.reason)
                    }
                    is AuthState.Unlocked -> {
                        val key = "app-${s.session}"
                        LaunchedEffect(key) {
                            lastSession?.takeIf { it != key }?.let(saveable::removeState)
                            lastSession = key
                        }
                        // While open, lock exactly when the 3-hour session or the day ends.
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(auth.millisUntilExpiry().coerceIn(1_000L, 30_000L))
                                auth.check()
                            }
                        }
                        saveable.SaveableStateProvider(key) { AppRoot(role = s.role, session = s.session) }
                    }
                }
            }
        }
    }
}
