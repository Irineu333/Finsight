plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.budgets.api)
            implementation(projects.feature.categories.api)

            implementation(projects.feature.categories.ui)

            implementation(projects.feature.accounts.ui)
            implementation(projects.feature.recurring.api)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.transactions.ui)
            implementation(projects.core.database)
            implementation(projects.core.ui)
            implementation(projects.core.analytics)
            implementation(projects.core.utils)
            implementation(libs.kotlinx.datetime)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.feature.budgets.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.budgets.impl"
}
