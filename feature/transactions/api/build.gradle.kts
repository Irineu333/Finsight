plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
            api(projects.core.domain)

            api(projects.feature.creditCards.api)

            api(projects.feature.categories.api)

            api(projects.feature.accounts.api)
            api(projects.core.utils)
            implementation(projects.core.ui)
            api(libs.androidx.navigation.compose)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.transactions.api"
}