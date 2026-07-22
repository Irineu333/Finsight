plugins {
    id("finsight.room.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)
            api(projects.core.ledger)
            implementation(projects.core.model)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
