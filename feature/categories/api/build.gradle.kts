plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.categories.api"
}