plugins {
    id("finsight.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.gitlive.firebase.crashlytics)
        }
        iosMain.dependencies {
            implementation(libs.gitlive.firebase.crashlytics)
        }
    }
}
