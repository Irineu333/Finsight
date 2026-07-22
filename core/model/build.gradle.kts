plugins {
    id("finsight.compose.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.resources)
            // The facades project onto the ledger — a recurring names an account,
            // a form holds the one the user picked. The arrow runs this way and
            // only this way: the ledger cannot see a facade (design D1).
            api(projects.core.ledger)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.arrow.core)
        }
    }
}
