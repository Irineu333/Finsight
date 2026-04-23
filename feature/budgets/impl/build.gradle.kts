plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.budgets.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.recurring.api)
            implementation(projects.feature.transactions.api)
            implementation(projects.core.database)
            implementation(projects.core.ui)
            implementation(projects.core.analytics)
            implementation(projects.core.utils)
            implementation(libs.kotlinx.datetime)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.neoutils.finsight.feature.budgets.impl.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.budgets.impl"
}
