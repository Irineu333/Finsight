plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.platform)
            implementation(projects.core.utils)
            implementation(libs.findLibrary("kotlinx-datetime").get())
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
