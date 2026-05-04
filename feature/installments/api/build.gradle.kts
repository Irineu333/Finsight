plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)

            api(projects.feature.transactions.api)
            implementation(projects.core.analytics)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.installments.api"
}
