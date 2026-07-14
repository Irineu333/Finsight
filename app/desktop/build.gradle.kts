import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(projects.app.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.koin.core)
    implementation(libs.multiplatform.settings)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gitlive.firebase.app)
    implementation(libs.firebase.java.sdk)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.multiplatform.settings)
}

compose.desktop {
    application {
        mainClass = "com.neoutils.finsight.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Finsight"
            packageVersion = "1.8.0"
            description = "Finsight finance app"
            vendor = "NeoUtils"

            val icons = project.file("src/main/resources/icons")

            windows {
                iconFile.set(icons.resolve("icon.ico"))
                // Stable UUID required so Windows treats new installs as upgrades
                // of the same product. Never change this value.
                upgradeUuid = "8d0f5c2e-7b3a-4a1e-9f6c-2d4b6e8a0c11"
                menuGroup = "Finsight"
                perUserInstall = true
                dirChooser = true
                shortcut = true
            }

            macOS {
                iconFile.set(icons.resolve("icon.icns"))
                bundleID = "com.neoutils.finsight"
            }

            linux {
                iconFile.set(icons.resolve("icon.png"))
            }
        }
    }
}
