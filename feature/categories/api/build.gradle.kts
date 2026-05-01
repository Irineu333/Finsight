plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
            api(projects.core.domain)
            implementation(projects.core.ui)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.categories.api"
}
