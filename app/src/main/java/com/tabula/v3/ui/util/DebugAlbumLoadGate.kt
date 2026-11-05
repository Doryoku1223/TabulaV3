package com.tabula.v3.ui.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/** In-memory, one-request test handshake. MainActivity calls this only in debug builds. */
object DebugAlbumLoadGate {
    class Request internal constructor(val albumId: String) {
        @Volatile var entered: Boolean = false
            private set
        @Volatile var completed: Boolean = false
            private set
        private val releaseSignal = CompletableDeferred<Unit>()

        fun release() { releaseSignal.complete(Unit) }

        internal suspend fun awaitRelease() {
            // Completion means the original loading effect has settled, including
            // its cancellation/late-result guard, rather than only that the gate opened.
            val caller = currentCoroutineContext()[Job]
            caller?.invokeOnCompletion { completed = true }
            entered = true
            try {
                // Deliver an already-loaded result even after its original caller is cancelled.
                withContext(NonCancellable) { releaseSignal.await() }
            } finally {
                if (caller == null) completed = true
            }
        }
    }

    private var armed: Request? = null

    @Synchronized fun arm(albumId: String): Request {
        check(armed == null) { "An album load gate is already armed" }
        return Request(albumId).also { armed = it }
    }

    @Synchronized private fun claim(albumId: String): Request? {
        val request = armed?.takeIf { it.albumId == albumId } ?: return null
        armed = null
        return request
    }

    suspend fun awaitIfArmed(albumId: String) { claim(albumId)?.awaitRelease() }

    @Synchronized fun reset() {
        armed?.release()
        armed = null
    }
}
