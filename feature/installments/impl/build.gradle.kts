plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.installments.api)
            implementation(projects.feature.transactions.api)

            implementation(projects.feature.categories.ui)

            implementation(projects.feature.accounts.ui)
            implementation(projects.feature.creditCards.api)
            implementation(projects.feature.creditCards.ui)
            implementation(projects.feature.categories.api)
            implementation(projects.core.database)
            implementation(projects.core.ui)
            implementation(projects.core.sharedui)
            implementation(projects.core.analytics)
            implementation(libs.kotlinx.datetime)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.feature.installments.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.installments.impl"
}
