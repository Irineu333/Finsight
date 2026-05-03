plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.utils)
            implementation(projects.core.domain)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditCards.api)
            api(libs.androidx.navigation.compose)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.transactions.ui"
}
