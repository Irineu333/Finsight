plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.utils)
            implementation(projects.feature.budgets.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.categories.ui)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.feature.budgets.ui.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.budgets.ui"
}
