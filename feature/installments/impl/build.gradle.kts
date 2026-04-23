plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.installments.api)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.creditCards.api)
            implementation(projects.feature.categories.api)
            implementation(projects.core.database)
            implementation(projects.core.ui)
            implementation(projects.core.analytics)
            implementation(libs.kotlinx.datetime)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.neoutils.finsight.feature.installments.impl.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.installments.impl"
}
