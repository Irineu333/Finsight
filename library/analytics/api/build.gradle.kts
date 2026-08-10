plugins {
    id("finsight.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The events name what the user did, and they say it with the facades
            // themselves — a transaction, a recurring, a category.
            implementation(projects.core.model)
        }
    }
}
