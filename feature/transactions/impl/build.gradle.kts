plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.transactions.api)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditCards.api)
            implementation(projects.feature.installments.api)
            implementation(projects.feature.recurring.api)
            implementation(projects.core.database)
            implementation(projects.core.ui)
            implementation(projects.core.sharedui)
            implementation(projects.core.analytics)
            implementation(projects.core.utils)
            implementation(libs.kotlinx.datetime)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.feature.transactions.impl.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.transactions.impl"
}
