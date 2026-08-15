plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.mcp.api)

            // The journal's lines lead to the entity they touched, and a transaction is opened
            // through the modal the rest of the app opens it with.
            implementation(projects.feature.transactions.api)

            implementation(projects.core.analytics)
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.designsystem)
            implementation(projects.core.navigation)
            implementation(projects.core.resources)
            implementation(projects.core.ui)

            implementation(libs.multiplatform.settings)

            // The journal is stamped with an instant, and the screen shows a civil date and time.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.multiplatform.settings.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.turbine)

            // The journal's suite seeds a real `transactions` row to assert that pruning a
            // record leaves it intact, and that entity is dated with `LocalDate`.
            implementation(libs.kotlinx.datetime)
        }
    }
}
