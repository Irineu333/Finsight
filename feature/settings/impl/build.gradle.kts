plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.designsystem)
            implementation(projects.core.ui)
            implementation(projects.core.resources)
            implementation(projects.core.analytics)
            implementation(projects.core.crashlytics)

            implementation(projects.feature.settings.api)

            // The integrations group: Settings hosts the MCP section — it names its route and builds
            // its graph inside its own, through the feature's entry point — and reads the platform
            // axis that decides whether the door into it is offered at all.
            implementation(projects.feature.mcp.api)
            implementation(projects.feature.shell.api)

            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.multiplatform.settings)

            // The settings tiles live here and in no other module, for the same reason
            // Ktor does: a settings screen is the only place that renders a preference,
            // and a component in `:core:designsystem` would offer every feature a second
            // vocabulary of rows next to the one this app already speaks.
            implementation(libs.compose.settings.ui.tiles)

            // Ktor lives here and in no other module. A `:core:network` would invite any
            // feature to reach the network, which is the opposite of what currency
            // consolidation wants: the remote source is a writer of the archive and never
            // a path of reading it, and the module graph is what holds that restriction
            // up (design D11).
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.turbine)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
