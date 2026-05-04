plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)

            api(projects.feature.categories.api)
            implementation(projects.core.database)
            implementation(projects.core.ui)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.recurring.api"
}
