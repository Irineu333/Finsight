package com.neoutils.finsight.convention

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
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
        // The common suite is compiled for iOS but no executable is produced from it.
        //
        // The compilation is worth keeping: it is what proves the shared code and its
        // tests stay Kotlin/Native-legal — a test named with a comma, say, compiles on the
        // JVM and does not compile here. Linking is a different matter. The Firebase
        // services this app is built on reach iOS as Objective-C frameworks that only
        // Xcode resolves, through SPM; Gradle has no copy of them and no way to obtain
        // one, so `ld` fails with `framework 'FirebaseCore' not found` the moment it has
        // to produce a real binary. The app itself is spared because a static framework
        // never links — a test executable does.
        //
        // So the link is off and so is the run task that would need its output. `allTests`
        // then reports on the suites that can actually run here, JVM and Android, instead
        // of failing on one that cannot run anywhere.
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.compilerOptions {
                freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
            }
            iosTarget.binaries.getTest(NativeBuildType.DEBUG)
                .linkTaskProvider.configure { enabled = false }
        }
        tasks.withType(KotlinNativeTest::class.java).configureEach { enabled = false }

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

internal fun Project.verifyFeatureDependencyRules(isApi: Boolean) {
    afterEvaluate {
        val violations = mutableListOf<String>()
        configurations.forEach { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                val depPath = dependency.path
                if (depPath == path) return@forEach
                val isCore = depPath.startsWith(":core:")
                val isFeature = depPath.startsWith(":feature:")
                if (!isCore && !isFeature) return@forEach

                if (isApi) {
                    if (!isCore) {
                        violations += "api '$path' não pode depender de '$depPath' " +
                            "(regra: api só depende de :core:*)"
                    }
                } else {
                    if (!isCore && !depPath.endsWith(":api")) {
                        violations += "impl '$path' não pode depender de '$depPath' " +
                            "(regra: impl só depende de :feature:*:api e :core:*)"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                "Violação das regras de dependência de módulos:\n" +
                    violations.joinToString("\n") { " - $it" }
            )
        }
    }
}

internal fun Project.verifyAppSharedDependencyRules() {
    afterEvaluate {
        val violations = mutableListOf<String>()
        configurations.forEach { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                val depPath = dependency.path
                if (depPath == path) return@forEach
                val isCore = depPath.startsWith(":core:")
                val isFeature = depPath.startsWith(":feature:")
                if (!isCore && !isFeature) {
                    violations += "app '$path' não pode depender de '$depPath' " +
                        "(regra: :app:shared só depende de :core:* e :feature:*)"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                "Violação das regras de dependência de módulos:\n" +
                    violations.joinToString("\n") { " - $it" }
            )
        }
    }
}
