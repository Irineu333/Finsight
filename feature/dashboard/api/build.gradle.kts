plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.home.api)
            api(libs.androidx.navigation.compose)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.dashboard.api"
}
