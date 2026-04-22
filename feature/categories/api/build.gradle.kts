plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
            api(projects.core.domain)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.categories.api"
}