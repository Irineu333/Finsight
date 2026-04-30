plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.dashboard.api)
            implementation(projects.core.ui)
            implementation(projects.core.utils)
            implementation(projects.core.platform)
            implementation(projects.core.analytics)
            implementation(projects.core.database)
            implementation(projects.core.domain)
            implementation(projects.feature.home.api)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditCards.api)
            implementation(projects.feature.budgets.api)
            implementation(projects.feature.recurring.api)
            implementation(libs.kotlinx.datetime)
            implementation("sh.calvin.reorderable:reorderable:3.0.0")
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.neoutils.finsight.feature.dashboard.impl.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.dashboard.impl"
}
