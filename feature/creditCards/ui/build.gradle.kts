plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.utils)
            implementation(projects.core.domain)
            implementation(projects.feature.creditCards.api)
            implementation(libs.kotlinx.datetime)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.creditCards.ui"
}
