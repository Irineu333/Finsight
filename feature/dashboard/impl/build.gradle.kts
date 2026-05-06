plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.dashboard.ui)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.transactions.ui)

            implementation(projects.feature.categories.ui)

            implementation(projects.feature.accounts.ui)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditCards.api)
            implementation(projects.feature.creditCards.ui)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.recurring.api)
            implementation(projects.feature.budgets.api)
            implementation(projects.feature.budgets.ui)
            implementation(projects.core.database)
            implementation(projects.core.ui)
            implementation(projects.feature.home.api)
            implementation(projects.core.platform)
            implementation(projects.core.analytics)
            implementation(projects.core.utils)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation("sh.calvin.reorderable:reorderable:3.0.0")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.feature.dashboard.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.dashboard.impl"
}
