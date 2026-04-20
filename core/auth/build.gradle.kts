plugins {
    id("kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
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

android {
    namespace = "com.neoutils.finsight.core.auth"
}
