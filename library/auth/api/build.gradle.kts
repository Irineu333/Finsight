plugins {
    id("finsight.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `getUserId` states its failure in the return type.
            api(libs.arrow.core)
        }
    }
}
