package com.neoutils.finsight.firebase

import com.google.firebase.FirebasePlatform
import java.io.File
import java.util.Properties

/**
 * [FirebasePlatform] do desktop: persiste os dados key/value do `firebase-java-sdk`
 * (inclusive a sessão de Auth anônima) em arquivo sob o diretório de dados do usuário,
 * permitindo que a sessão sobreviva entre execuções.
 */
internal class DesktopFirebasePlatform(
    private val dataDir: File,
) : FirebasePlatform() {

    private val storeFile = File(dataDir, "firebase-store.properties")

    private val properties = Properties().apply {
        if (storeFile.exists()) storeFile.inputStream().use { load(it) }
    }

    override fun store(key: String, value: String) {
        properties.setProperty(key, value)
        persist()
    }

    override fun retrieve(key: String): String? = properties.getProperty(key)

    override fun clear(key: String) {
        properties.remove(key)
        persist()
    }

    override fun log(msg: String) {
        println("[Firebase] $msg")
    }

    override fun getDatabasePath(name: String): File = File(dataDir, name)

    private fun persist() {
        dataDir.mkdirs()
        storeFile.outputStream().use { properties.store(it, null) }
    }
}
