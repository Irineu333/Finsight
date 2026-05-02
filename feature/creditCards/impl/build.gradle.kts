plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.creditCards.api)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.categories.api)
            implementation(projects.core.database)
            implementation(projects.core.ui)
            implementation(projects.core.sharedui)
            implementation(projects.feature.home.api)
            implementation(projects.core.analytics)
            implementation(projects.core.utils)
            implementation(libs.kotlinx.datetime)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.feature.creditCards.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.creditCards.impl"
}
