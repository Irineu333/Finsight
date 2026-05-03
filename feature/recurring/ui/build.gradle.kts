plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.utils)
            implementation(projects.feature.recurring.api)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditCards.api)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.recurring.ui"
}
