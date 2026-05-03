plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.utils)
            implementation(projects.feature.installments.api)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.creditCards.api)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.installments.ui"
}
