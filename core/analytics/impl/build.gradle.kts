plugins {
    id("finsight.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.analytics.api)

            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.gitlive.firebase.analytics)
        }
        iosMain.dependencies {
            implementation(libs.gitlive.firebase.analytics)
        }
    }
}
