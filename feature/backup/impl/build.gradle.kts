plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            // The database is what captures, verifies and replaces its own content
            // (design D7); this module is what calls any of it "backup" and what turns
            // its refusals into a sentence a person reads.
            implementation(projects.core.database)
            implementation(projects.core.resources)

            implementation(projects.feature.backup.api)

            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}
