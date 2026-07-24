plugins {
    id("finsight.room.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)

            // No project dependency at all: the ledger sees no facade, not even a
            // model of one. Its errors carry a message and a type; turning either
            // into a sentence needs the string resources, and that lives with the
            // facades in `:core:model` (design D1, amended) precisely so this list
            // can stay empty.
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.turbine)
        }
    }
}
