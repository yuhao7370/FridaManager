package com.yuhao7370.fridamanager.root

object ShellEscaper {
    fun quote(raw: String): String = "'${raw.replace("'", "'\"'\"'")}'"
}
