package com.neoutils.finsight.firebase

import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import java.io.File

/**
 * Inicializa o `firebase-java-sdk` no boot do desktop: registra o [DesktopFirebasePlatform] e
 * chama `Firebase.initialize` com [dev.gitlive.firebase.FirebaseOptions] derivados do
 * `google-services.json` bundlado. Deve rodar antes do `startKoin`, pois a resolução de
 * `ISupportRepository` depende do Firestore/Auth já inicializados.
 */
internal object DesktopFirebase {

    fun initialize(dataDir: File = UserDataDirectory.resolve()) {
        FirebasePlatform.initializeFirebasePlatform(DesktopFirebasePlatform(dataDir))

        val config = requireNotNull(
            javaClass.getResourceAsStream("/google-services.json"),
        ) { "Missing bundled google-services.json in desktop resources." }
            .bufferedReader().use { it.readText() }

        Firebase.initialize(context = null, options = GoogleServicesParser.parse(config))
    }
}
