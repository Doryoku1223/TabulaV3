package com.tabula.v3.permission

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal class ControlledDeleteResultRegistry : ActivityResultRegistry() {
    var lastCode = -1
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
        input: I, options: ActivityOptionsCompat?) { lastCode = requestCode }
}

/** Controlled system-result delivery, using the real Activity lifecycle/registry and production session. */
class DeletePermissionRecoveryActivity : ComponentActivity() {
    internal lateinit var session: DeletePermissionSession
    internal val registry = ControlledDeleteResultRegistry()
    lateinit var request: () -> Unit
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = DeletePermissionSession(savedInstanceState)
        savedInstanceState?.getBundle("registry")?.let { registry.onRestoreInstanceState(it) }
        val launcher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult(), registry) {
            session.complete(it.resultCode == Activity.RESULT_OK)
        }
        request = {
            val sender = android.app.PendingIntent.getBroadcast(this, 1, android.content.Intent("fixture"),
                android.app.PendingIntent.FLAG_IMMUTABLE).intentSender
            session.launch({}, { launcher.launch(IntentSenderRequest.Builder(sender).build()) })
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        session.save(outState)
        val registryState = Bundle()
        registry.onSaveInstanceState(registryState)
        outState.putBundle("registry", registryState)
        super.onSaveInstanceState(outState)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DeletePermissionActivityRecoveryTest {
    @Test fun resultAfterActivityRecreationSignalsReconciliationRatherThanLaunchingAgain() {
        val first = Robolectric.buildActivity(DeletePermissionRecoveryActivity::class.java).create().start().resume()
        first.get().request()
        val code = first.get().registry.lastCode
        val saved = Bundle()
        first.saveInstanceState(saved).pause().stop().destroy()
        val restored = Robolectric.buildActivity(DeletePermissionRecoveryActivity::class.java).create(saved).start().resume()
        assertTrue(restored.get().session.inFlight)
        assertEquals(-1, restored.get().registry.lastCode)
        assertTrue(restored.get().registry.dispatchResult(code, Activity.RESULT_OK, null))
        assertFalse(restored.get().session.inFlight)
        assertEquals(1, restored.get().session.recoveryRevision)
        assertEquals(-1, restored.get().registry.lastCode)
        restored.pause().stop().destroy()
    }
}
