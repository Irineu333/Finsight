plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.kotlinx.coroutinesCore)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.budgets.api"
}