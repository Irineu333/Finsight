plugins {
    id("finsight.room.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)
            api(projects.core.model)
        }
    }
}
