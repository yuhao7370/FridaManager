package com.yuhao7370.fridamanager.root

class FilePermissionController(private val shell: RootShellManager) {
    suspend fun ensureExecutable(path: String): ShellResult {
        val escaped = ShellEscaper.quote(path)
        val command = "chmod 755 $escaped && test -x $escaped"
        return shell.exec(command)
    }
}
