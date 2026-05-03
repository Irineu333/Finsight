plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.utils)
            implementation(projects.feature.accounts.api)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.accounts.ui"
}
