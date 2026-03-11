package com.yuhao7370.fridamanager.root

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class RootShellManager(context: Context) {
    private val appContext = context.applicationContext
    private val shellMutex = Mutex()

    suspend fun hasRootAccess(): Boolean = withRootShell { shell ->
        if (!shell.isRoot) {
            false
        } else {
            val stdout = mutableListOf<String>()
            val result = shell.newJob().add("id -u").to(stdout, mutableListOf()).exec()
            result.isSuccess && stdout.any { it.trim() == "0" }
        }
    }

    suspend fun exec(command: String): ShellResult = execScript(listOf(command))

    suspend fun execScript(lines: List<String>): ShellResult = withRootShell { shell ->
        if (!shell.isRoot) {
            return@withRootShell ShellResult(
                exitCode = 1,
                stdout = "",
                stderr = "Root shell unavailable"
            )
        }

        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val result = shell.newJob()
            .add(*lines.toTypedArray())
            .to(stdout, stderr)
            .exec()

        ShellResult(
            exitCode = result.code,
            stdout = stdout.joinToString("\n"),
            stderr = stderr.joinToString("\n")
        )
    }

    private suspend fun <T> withRootShell(block: (Shell) -> T): T = withContext(Dispatchers.IO) {
        shellMutex.withLock {
            val shell = Shell.Builder.create()
                .setContext(appContext)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(15)
                .build()
            try {
                block(shell)
            } finally {
                closeQuietly(shell)
            }
        }
    }

    private fun closeQuietly(shell: Shell?) {
        if (shell == null) return
        runCatching {
            shell.close()
        }.recoverCatching {
            if (it !is IOException) throw it
        }
    }
}
