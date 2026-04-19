plugins {
    id("kmp-compose")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("koin-core").get())
            implementation(libs.findLibrary("koin-compose").get())
            implementation(libs.findLibrary("koin-compose-viewmodel").get())
            implementation(libs.findLibrary("arrow-core").get())
            implementation(libs.findLibrary("androidx-lifecycle-viewmodelCompose").get())
            implementation(libs.findLibrary("androidx-lifecycle-runtimeCompose").get())
            implementation(libs.findLibrary("androidx-navigation-compose").get())
        }
        androidMain.dependencies {
            implementation(libs.findLibrary("koin-android").get())
            implementation(libs.findLibrary("androidx-activity-compose").get())
        }
    }
}
