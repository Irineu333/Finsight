plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.utils)
            implementation(projects.feature.transactions.api)
            api(libs.androidx.navigation.compose)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.dashboard.ui"
}
