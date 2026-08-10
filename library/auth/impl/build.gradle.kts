plugins {
    id("finsight.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.library.auth.api)

            implementation(libs.arrow.core)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.gitlive.firebase.auth)
        }
        iosMain.dependencies {
            implementation(libs.gitlive.firebase.auth)
        }
    }
}
