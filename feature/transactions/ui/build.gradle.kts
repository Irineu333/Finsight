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
            implementation(projects.feature.creditCards.ui)
            implementation(libs.koin.compose)
            api(libs.androidx.navigation.compose)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.feature.transactions.ui.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.transactions.ui"
}
