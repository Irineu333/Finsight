plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
            api(projects.core.domain)
            implementation(projects.core.database)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.recurring.api"
}
