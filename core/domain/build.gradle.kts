plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(projects.core.utils)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.core.domain"
}