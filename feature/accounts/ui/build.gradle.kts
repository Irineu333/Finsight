plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.utils)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.transactions.api)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.feature.accounts.ui.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.accounts.ui"
}
