plugins {
    id("finsight.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: `TransactionUi.amount` is a `DisplayAmount`,
            // so `:core:common` is part of this module's public surface.
            api(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.model)
            implementation(projects.core.resources)

            implementation(libs.kotlinx.datetime)
        }
    }
}
