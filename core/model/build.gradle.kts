plugins {
    id("finsight.compose.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: the consolidation layer returns a money figure,
            // so the type is part of this module's surface and every consumer of a
            // consolidated figure needs it.
            api(projects.core.common)
            implementation(projects.core.resources)
            // The facades project onto the ledger — a recurring names an account,
            // a form holds the one the user picked. The arrow runs this way and
            // only this way: the ledger cannot see a facade (design D1).
            api(projects.core.ledger)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.arrow.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
