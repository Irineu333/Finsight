import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/**
 * Records what Compose Desktop packs into the distribution image, so a test can ask what the
 * user actually installs instead of what the build happens to have on hand.
 *
 * The source is `runtimeClasspath`, the configuration `createDistributable` copies into
 * `Finsight.app/Contents/app`: every jar there is one of these, under the same name plus the
 * content hash the packaging step appends. Each line is the artifact's origin and its jar,
 * separated by `|` — the origin because jars built from this repository are all named
 * `api-jvm.jar` or `impl-jvm.jar`, and only the component id says which module they are.
 */
abstract class WriteDistributionManifest : DefaultTask() {

    @get:Input
    abstract val artifacts: ListProperty<String>

    @get:OutputFile
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun write() {
        manifest.get().asFile.apply {
            parentFile.mkdirs()
            writeText(artifacts.get().sorted().joinToString(separator = "\n", postfix = "\n"))
        }
    }
}

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

val writeDistributionManifest = tasks.register<WriteDistributionManifest>("writeDistributionManifest") {
    val runtimeClasspath = configurations.named("runtimeClasspath")
    dependsOn(runtimeClasspath)
    artifacts.set(
        runtimeClasspath
            .flatMap { it.incoming.artifacts.resolvedArtifacts }
            .map { resolved ->
                resolved.map { "${it.id.componentIdentifier.displayName}|${it.file.name}" }
            }
    )
    manifest.set(layout.buildDirectory.file("generated/distribution/distribution-classpath.txt"))
}

tasks.named<Copy>("processTestResources") {
    from(writeDistributionManifest)
}

// `./gradlew jvmTest` is the project's "all tests" command, and `kotlin("jvm")` names this
// module's test task `test`: without the alias the desktop suite — including the guard that
// the MCP server ships inside the distribution — sits outside the command that is supposed
// to run everything.
tasks.register("jvmTest") {
    dependsOn(tasks.named("test"))
}

compose.desktop {
    application {
        mainClass = "com.neoutils.finsight.MainKt"

        // ProGuard fails on ~9850 unresolved references pulled in by firebase-java-sdk,
        // okhttp and slf4j (android.*, conscrypt, slf4j.impl). Minification isn't worth
        // maintaining a -dontwarn list for a desktop app, so disable it.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)

            // ./gradlew :app:desktop:suggestRuntimeModules
            modules(
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.naming",
                "java.prefs",
                "java.sql",
                "jdk.unsupported",
            )

            packageName = "Finsight"
            packageVersion = "1.10.0"
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
