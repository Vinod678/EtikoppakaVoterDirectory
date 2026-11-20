package com.example.etikoppakavoterdirectory

import android.app.Application
import android.os.SystemClock
import com.example.etikoppakavoterdirectory.auth.AuthManager
import com.example.etikoppakavoterdirectory.auth.KeystoreAuthStore
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

class VoterDirectoryApp : Application() {
    val repository: VoterRepository by lazy {
        VoterRepository(
            db = VoterDatabase.get(this),
            readAsset = { name -> assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() } },
            safetyDir = File(filesDir, "safety"),
            listAssets = { dir -> assets.list(dir)?.toList().orEmpty() },
            // Viewer sessions (and a locked app) can never change data.
            canEdit = { auth.canEdit },
        )
    }
    val messages = UserMessages()

    /** App password session. In memory only, so every new process starts locked. */
    val auth: AuthManager by lazy { AuthManager(KeystoreAuthStore(this), SystemClock::elapsedRealtime) }

    override fun onCreate() {
        super.onCreate()
        // Temporary PDF / image exports contain personal data; never keep them between app runs.
        Thread { File(cacheDir, "exports").listFiles()?.forEach { it.deleteRecursively() } }.start()
    }
}

/** A user-facing message. [isError] messages stay longer and can be dismissed. */
data class UserMessage(val text: String, val isError: Boolean)

/** Short user-facing messages (shown as snackbars). Never contains personal data. */
class UserMessages {
    private val flow = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<UserMessage> = flow
    fun show(message: String) {
        flow.tryEmit(UserMessage(message, isError = false))
    }
    fun error(message: String) {
        flow.tryEmit(UserMessage(message, isError = true))
    }
}
