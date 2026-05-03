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
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.budgets.ui"
}
