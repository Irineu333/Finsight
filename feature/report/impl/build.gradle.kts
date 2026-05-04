plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.report.api)
            implementation(projects.core.ui)

            implementation(projects.feature.categories.ui)

            implementation(projects.feature.accounts.ui)
            implementation(projects.core.analytics)
            implementation(projects.core.utils)
            implementation(projects.core.database)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditCards.api)
            implementation(projects.feature.creditCards.ui)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.transactions.ui)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.feature.report.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.report.impl"
}
