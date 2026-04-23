plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.installments.api"
}
