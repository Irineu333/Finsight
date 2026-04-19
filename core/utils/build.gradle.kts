plugins {
    id("kmp-library")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("kotlinx-datetime").get())
            implementation(libs.findLibrary("kotlinx-coroutinesCore").get())
        }
    }
}

android {
    namespace = "com.neoutils.finsight.core.utils"
}
