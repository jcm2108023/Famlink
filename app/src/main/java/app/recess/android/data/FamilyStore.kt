package app.recess.android.data

import android.util.Log
import androidx.core.util.AtomicFile
import app.recess.core.FamilyJson
import app.recess.core.FamilyState
import app.recess.core.Seed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.File

/**
 * The family ledger, kept in memory as a [StateFlow] and persisted as JSON in app storage
 * (the Android counterpart of the web app's localStorage). Writes are conflated and off the main thread.
 */
class FamilyStore(file: File, scope: CoroutineScope) {
    private val atomicFile = AtomicFile(file)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<FamilyState> = _state.asStateFlow()

    private val pending = Channel<FamilyState>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            for (snapshot in pending) write(snapshot)
        }
    }

    fun update(transform: (FamilyState) -> FamilyState): FamilyState {
        val next = _state.updateAndGet(transform)
        pending.trySend(next)
        return next
    }

    private fun load(): FamilyState = runCatching {
        atomicFile.baseFile.takeIf { it.exists() }?.let { FamilyJson.decode(atomicFile.readFully().decodeToString()) }
    }.onFailure { Log.w(TAG, "Could not read saved family; starting from the demo", it) }
        .getOrNull() ?: Seed.create()

    private fun write(snapshot: FamilyState) {
        val out = atomicFile.startWrite()
        try {
            out.write(FamilyJson.encode(snapshot).encodeToByteArray())
            atomicFile.finishWrite(out)
        } catch (e: Exception) {
            atomicFile.failWrite(out)
            Log.w(TAG, "Could not save family", e)
        }
    }

    private companion object {
        const val TAG = "FamilyStore"
    }
}
