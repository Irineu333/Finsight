plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(projects.feature.recurring.api)
            implementation(libs.kotlinx.coroutinesCore)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.budgets.api"
}