plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.platform)
            implementation(projects.core.utils)
            implementation(libs.findLibrary("kotlinx-datetime").get())
            implementation(libs.findLibrary("koin-core").get())
            implementation(libs.findLibrary("koin-compose").get())
            implementation(libs.findLibrary("androidx-lifecycle-viewmodelCompose").get())
            implementation(libs.findLibrary("androidx-lifecycle-runtimeCompose").get())
        }
        androidMain.dependencies {
            implementation(libs.findLibrary("koin-android").get())
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.neoutils.finsight.core.ui.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.core.ui"
}
