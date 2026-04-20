plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.koin.core)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.core.utils"
}
