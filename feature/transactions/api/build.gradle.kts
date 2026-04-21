plugins {
    id("kmp-library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(projects.core.utils)
            api(projects.feature.accounts.api)
            api(projects.feature.categories.api)
            api(projects.feature.creditCards.api)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.transactions.api"
}