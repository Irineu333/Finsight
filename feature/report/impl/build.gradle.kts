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
            implementation(projects.core.analytics)
            implementation(projects.core.utils)
            implementation(projects.core.database)
            implementation(projects.core.domain)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditCards.api)
            implementation(projects.feature.transactions.api)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.neoutils.finsight.feature.report.impl.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.report.impl"
}
