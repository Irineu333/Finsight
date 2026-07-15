package com.neoutils.finsight.firebase

import java.io.File

/** Resolve o diretório de dados do usuário por-OS onde o Finsight desktop persiste seus arquivos. */
internal object UserDataDirectory {

    fun resolve(appName: String = "Finsight"): File {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home").orEmpty()
        val base = when {
            os.contains("win") -> System.getenv("APPDATA") ?: "$home\\AppData\\Roaming"
            os.contains("mac") -> "$home/Library/Application Support"
            else -> System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
        }
        return File(base, appName)
    }
}
