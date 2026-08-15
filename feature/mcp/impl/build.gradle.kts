plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.mcp.api)

            implementation(projects.core.analytics)
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.designsystem)
            implementation(projects.core.navigation)
            implementation(projects.core.resources)
            implementation(projects.core.ui)

            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(libs.multiplatform.settings.test)
            implementation(libs.kotlinx.coroutinesTest)

            // The journal's suite seeds a real `transactions` row to assert that pruning a
            // record leaves it intact, and that entity is dated with `LocalDate`.
            implementation(libs.kotlinx.datetime)
        }
    }
}
