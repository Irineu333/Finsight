package com.neoutils.finsight.backup.service

import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import com.neoutils.finsight.extension.PlatformContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The storage access framework, reached without a lifecycle owner to reach it through.
 *
 * A file or a folder is asked for from a view model, not from a composition, so there is no
 * `rememberLauncherForActivityResult` to lean on and no owner whose destruction would tidy
 * a registration away. What there is instead is the registry the activity already carries
 * and an obligation to leave it as clean as it was found — [awaitResult] is where that
 * obligation is discharged.
 *
 * It is a file of its own because both halves of this feature's reach into the world go
 * through it: [AndroidBackupFileService] raises the two document dialogs, and
 * [AndroidBackupFolder] raises the folder picker. The plumbing is the same in both and the
 * hazards it avoids are subtle enough that a second copy of it would be a second place to
 * get them wrong.
 */

/**
 * The registry the activity already owns.
 *
 * The cast holds because of how the context is built: `ProvidePlatformContext` takes
 * `LocalActivityResultRegistryOwner.current` and narrows it to `Activity`, so what is in
 * hand is an [ActivityResultRegistryOwner] that only its declared type hides.
 */
internal val PlatformContext.registry: ActivityResultRegistry
    get() = (activity as ActivityResultRegistryOwner).activityResultRegistry

/**
 * Suspends until [contract] answers, and takes the registration back out on every way this
 * function can be left.
 *
 * The three-argument [ActivityResultRegistry.register] is the overload that takes no
 * lifecycle owner, and that is why it is the one used: the four-argument one throws once
 * the activity is past `STARTED`, which is the only moment a screen ever asks for a file.
 * The price of skipping it is that nothing unregisters on this code's behalf — a callback
 * left behind holds the continuation, and through it everything the coroutine captured, for
 * as long as the activity lives. So all four exits unregister: the callback firing, the
 * continuation being cancelled, `launch` throwing before there is anything to wait for, and
 * the replay `register` performs when the key it is given already has a result waiting.
 * Exactly one of them gets to do it — the launcher is taken out of an [AtomicReference], so
 * the cancellation handler, which runs on whichever thread cancelled, cannot race the
 * callback, which runs on the main one.
 *
 * The key is minted per call, so a result addressed to the key of a dead process is never
 * claimed by the next one. That is the intended outcome rather than a leak: the process
 * dying with the picker open takes the continuation with it, and until a file arrives
 * nothing has been changed that a missing result could leave half done.
 */
internal suspend fun <I, O> ActivityResultRegistry.awaitResult(
    contract: ActivityResultContract<I, O>,
    input: I,
): O = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        val key = "$REGISTRY_KEY_PREFIX${UUID.randomUUID()}"
        val registered = AtomicReference<ActivityResultLauncher<I>?>(null)
        var answered = false

        val launcher = register(key, contract) { result ->
            answered = true
            registered.getAndSet(null)?.unregister()
            continuation.resume(result)
        }

        // Read before anything else can run on this thread, so it answers one question
        // only: did register() replay a pending result before handing the launcher back?
        // If it did, the callback had nothing to unregister with and this does.
        if (answered) {
            launcher.unregister()
            return@suspendCancellableCoroutine
        }

        registered.set(launcher)
        continuation.invokeOnCancellation {
            onMainThread { registered.getAndSet(null)?.unregister() }
        }

        try {
            launcher.launch(input)
        } catch (cause: Throwable) {
            // Nothing will call back, and this is not a cancellation, so neither of the
            // other two exits is going to run.
            registered.getAndSet(null)?.unregister()
            throw cause
        }
    }
}

private fun onMainThread(block: () -> Unit) {
    val mainLooper = Looper.getMainLooper()
    if (Looper.myLooper() == mainLooper) block() else Handler(mainLooper).post(block)
}

private const val REGISTRY_KEY_PREFIX = "backup-activity-result-"
