plugins {
    id("kmp-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.findLibrary("androidx-room-runtime").get())
            implementation(libs.findLibrary("androidx-sqlite-bundled").get())
            implementation(libs.findLibrary("kotlinx-datetime").get())
            implementation(libs.findLibrary("koin-core").get())
            implementation(projects.core.utils)
        }
        androidMain.dependencies {
            implementation(libs.findLibrary("koin-android").get())
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspCommonMainMetadata", libs.findLibrary("androidx-room-compiler").get())
    add("kspAndroid", libs.findLibrary("androidx-room-compiler").get())
    add("kspJvm", libs.findLibrary("androidx-room-compiler").get())
    add("kspIosSimulatorArm64", libs.findLibrary("androidx-room-compiler").get())
    add("kspIosX64", libs.findLibrary("androidx-room-compiler").get())
    add("kspIosArm64", libs.findLibrary("androidx-room-compiler").get())
}

android {
    namespace = "com.neoutils.finsight.core.database"
}