package com.neoutils.finsight.convention

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

private val Project.derivedNamespace: String
    get() = "com.neoutils.finsight." + path.removePrefix(":").replace(":", ".").replace("-", "")

/**
 * The group every Firebase binding in this build comes from. On iOS those bindings are
 * `cinterop`s over frameworks only Xcode can resolve, so a link that reaches one fails.
 */
private const val FIREBASE_GROUP = "dev.gitlive"

/**
 * Whether the given compile classpath is free of Firebase, and so can be linked into an
 * executable here. Resolved on demand, at execution time.
 */
private fun Project.linksWithoutFirebase(configurationName: String) = provider {
    configurations.getByName(configurationName)
        .incoming.resolutionResult.allComponents
        .none { it.moduleVersion?.group == FIREBASE_GROUP }
}

internal fun Project.configureKotlinMultiplatform() {
    with(pluginManager) {
        apply("org.jetbrains.kotlin.multiplatform")
        apply("com.android.library")
    }

    extensions.configure<KotlinMultiplatformExtension> {
        androidTarget {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
            }
        }
        jvm {
            compilerOptions {
                freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
            }
        }
        // A module whose iOS test binary would pull Firebase in produces no executable.
        //
        // The compilation is always worth keeping: it is what proves the shared code and
        // its tests stay Kotlin/Native-legal — a test named with a comma, say, compiles on
        // the JVM and does not compile here. Linking is a different matter. The Firebase
        // services this app is built on reach iOS as Objective-C frameworks that only
        // Xcode resolves, through SPM; Gradle has no copy of them and no way to obtain
        // one, so `ld` fails with `framework 'FirebaseCore' not found` the moment it has
        // to produce a real binary. The app itself is spared because a static framework
        // never links — a test executable does.
        //
        // The reach of that is the module's own link classpath, not the whole build: a
        // module that never sees Firebase links and runs its iOS suite like any other.
        // So the link is skipped, and with it the run task that would need its output,
        // exactly where the classpath says it cannot succeed.
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.compilerOptions {
                freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
            }
            val testBinary = iosTarget.binaries.getTest(NativeBuildType.DEBUG)
            val linkable = linksWithoutFirebase(testBinary.compilation.compileDependencyConfigurationName)
            testBinary.linkTaskProvider.configure { onlyIf { linkable.get() } }
            tasks.withType(KotlinNativeTest::class.java)
                .matching { it.name == "${iosTarget.targetName}Test" }
                .configureEach { onlyIf { linkable.get() } }
        }

        with(sourceSets) {
            all {
                languageSettings.enableLanguageFeature("ContextParameters")
            }
            getByName("commonTest").dependencies {
                implementation(libs.findLibrary("kotlin-test").get())
            }
            getByName("jvmTest").dependencies {
                implementation(libs.findLibrary("kotlin-testJunit").get())
            }
        }
    }

    extensions.configure<LibraryExtension> {
        namespace = derivedNamespace
        compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        defaultConfig {
            minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

internal fun Project.configureCompose() {
    with(pluginManager) {
        apply("org.jetbrains.compose")
        apply("org.jetbrains.kotlin.plugin.compose")
    }

    val compose = ComposePlugin.Dependencies(this)

    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.getByName("commonMain").dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)

            implementation(libs.findLibrary("androidx-lifecycle-viewmodelCompose").get())
            implementation(libs.findLibrary("androidx-lifecycle-runtimeCompose").get())
            implementation(libs.findLibrary("androidx-navigation-compose").get())
        }
        sourceSets.getByName("androidMain").dependencies {
            implementation(compose.preview)
            implementation(libs.findLibrary("androidx-activity-compose").get())
        }
        sourceSets.getByName("jvmMain").dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
