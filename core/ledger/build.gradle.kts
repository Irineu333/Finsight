plugins {
    id("finsight.room.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)

            // The ledger sees no facade — not even a model of one. `:core:common`
            // and `:core:resources` carry `UiText` and the strings its errors speak
            // in; neither knows what a category or an invoice is.
            implementation(projects.core.common)
            implementation(projects.core.resources)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.arrow.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.turbine)
        }
    }
}
