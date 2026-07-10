plugins {
    id("finsight.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.navigation.compose)
        }
    }
}
