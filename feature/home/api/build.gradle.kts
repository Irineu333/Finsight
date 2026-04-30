plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.transactions.api)
            api(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.home.api"
}
