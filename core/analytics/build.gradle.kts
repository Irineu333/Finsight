plugins {
    id("kmp-library")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("koin-core").get())
        }
        androidMain.dependencies {
            implementation(libs.findLibrary("gitlive-firebase-analytics").get())
            implementation(libs.findLibrary("gitlive-firebase-crashlytics").get())
        }
        iosMain.dependencies {
            implementation(libs.findLibrary("gitlive-firebase-analytics").get())
            implementation(libs.findLibrary("gitlive-firebase-crashlytics").get())
        }
    }
}

android {
    namespace = "com.neoutils.finsight.core.analytics"
}
