plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
            implementation(projects.core.utils)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.creditCards.api"
}
