plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }
    jvm {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework { isStatic = true }
        iosTarget.compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("androidx-room-runtime").get())
            implementation(libs.findLibrary("androidx-sqlite-bundled").get())
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
        }
        all {
            languageSettings.enableLanguageFeature("ContextParameters")
        }
    }
}

android {
    compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
    defaultConfig {
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspCommonMainMetadata", libs.findLibrary("androidx-room-compiler").get())
    add("kspAndroid", libs.findLibrary("androidx-room-compiler").get())
    add("kspJvm", libs.findLibrary("androidx-room-compiler").get())
    add("kspIosX64", libs.findLibrary("androidx-room-compiler").get())
    add("kspIosArm64", libs.findLibrary("androidx-room-compiler").get())
    add("kspIosSimulatorArm64", libs.findLibrary("androidx-room-compiler").get())
}
