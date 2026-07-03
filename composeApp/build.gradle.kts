import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }

    jvm() {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts += "-lsqlite3"
            binaryOption("bundleId", "com.neoutils.finsight.ComposeApp")

            // Export seletivo: apenas :core:* e :feature:*:api ficam visíveis ao Swift.
            // Os :feature:*:impl são linkados via implementation e permanecem invisíveis.
            export(projects.core.analytics)
            export(projects.core.auth)
            export(projects.core.common)
            export(projects.core.crashlytics)
            export(projects.core.database)
            export(projects.core.designsystem)
            export(projects.core.model)
            export(projects.core.resources)
            export(projects.core.ui)
            export(projects.feature.accounts.api)
            export(projects.feature.budgets.api)
            export(projects.feature.categories.api)
            export(projects.feature.creditcards.api)
            export(projects.feature.recurring.api)
            export(projects.feature.support.api)
            export(projects.feature.transactions.api)
        }
        iosTarget.compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.analytics)
            api(projects.core.auth)
            api(projects.core.common)
            api(projects.core.crashlytics)
            api(projects.core.database)
            api(projects.core.designsystem)
            api(projects.core.model)
            api(projects.core.resources)
            api(projects.core.ui)

            api(projects.feature.accounts.api)
            implementation(projects.feature.accounts.impl)
            api(projects.feature.budgets.api)
            implementation(projects.feature.budgets.impl)
            api(projects.feature.categories.api)
            implementation(projects.feature.categories.impl)
            api(projects.feature.creditcards.api)
            api(projects.feature.recurring.api)
            implementation(projects.feature.recurring.impl)
            api(projects.feature.support.api)
            implementation(projects.feature.support.impl)
            api(projects.feature.transactions.api)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.datetime)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.kotlinx.serialization.json)

            implementation("sh.calvin.reorderable:reorderable:3.0.0")

            // Firebase
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.auth)

            // Arrow
            implementation(libs.arrow.core)
            // implementation(libs.arrow.fx.coroutines)
            // implementation(libs.arrow.autoclose)
            // implementation(libs.arrow.resilience)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.gitlive.firebase.analytics)
            implementation(libs.gitlive.firebase.crashlytics)
        }
        iosMain.dependencies {
            implementation(libs.gitlive.firebase.analytics)
            implementation(libs.gitlive.firebase.crashlytics)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.testJunit)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        all {
            languageSettings.enableLanguageFeature("ContextParameters")
        }
    }
}

android {
    namespace = "com.neoutils.finsight"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.neoutils.finsight"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 27
        versionName = "1.8.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    val keystoreProperties = Properties().also { props ->
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use {
            props.load(it)
        }
    }

    signingConfigs {
        create("release") {
            storeFile = keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it) }
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.neoutils.finsight.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.neoutils.finsight"
            packageVersion = "1.8.0"
        }
    }
}

