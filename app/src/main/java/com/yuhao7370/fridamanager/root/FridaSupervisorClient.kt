package com.yuhao7370.fridamanager.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import com.yuhao7370.fridamanager.root.ipc.IFridaSupervisorService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

class FridaSupervisorClient(context: Context) {
    private val appContext = context.applicationContext
    private val serviceIntent = Intent(appContext, FridaSupervisorService::class.java)
    private val connectMutex = Mutex()

    @Volatile
    private var service: IFridaSupervisorService? = null

    @Volatile
    private var connectionDeferred: CompletableDeferred<IFridaSupervisorService>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val remote = IFridaSupervisorService.Stub.asInterface(binder)
            Log.i(TAG, "onServiceConnected name=$name binder=${binder != null}")
            service = remote
            connectionDeferred?.complete(remote)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "onServiceDisconnected name=$name")
            service = null
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "onBindingDied name=$name")
            service = null
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.e(TAG, "onNullBinding name=$name")
            connectionDeferred?.completeExceptionally(
                IllegalStateException("Root supervisor returned null binding")
            )
        }
    }

    suspend fun start(version: String, binaryPath: String, host: String, port: Int): ProcessInfo {
        val remote = ensureConnected()
        Log.i(TAG, "Calling root supervisor start version=$version path=$binaryPath host=$host port=$port")
        return SupervisorBundleCodec.decode(remote.start(version, binaryPath, host, port))
    }

    suspend fun stop(): ProcessInfo {
        val remote = ensureConnected()
        Log.i(TAG, "Calling root supervisor stop")
        return SupervisorBundleCodec.decode(remote.stop())
    }

    suspend fun restart(version: String, binaryPath: String, host: String, port: Int): ProcessInfo {
        val remote = ensureConnected()
        Log.i(TAG, "Calling root supervisor restart version=$version path=$binaryPath host=$host port=$port")
        return SupervisorBundleCodec.decode(remote.restart(version, binaryPath, host, port))
    }

    suspend fun status(): ProcessInfo {
        val remote = ensureConnected()
        return SupervisorBundleCodec.decode(remote.getStatus())
    }

    private suspend fun ensureConnected(): IFridaSupervisorService {
        service?.let { existing ->
            return existing
        }
        return connectMutex.withLock {
            val alreadyConnected = service
            if (alreadyConnected != null) {
                return@withLock alreadyConnected
            }
            val deferred = CompletableDeferred<IFridaSupervisorService>()
            connectionDeferred = deferred
            Log.i(TAG, "Binding root supervisor service")
            withContext(Dispatchers.Main.immediate) {
                RootService.bind(serviceIntent, appContext.mainExecutor, connection)
            }
            val connected = withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
            service = connected
            Log.i(TAG, "Root supervisor service connected")
            connected
        }
    }

    companion object {
        private const val TAG = "FridaSupervisorClient"
        private const val BIND_TIMEOUT_MS = 10_000L
    }
}
