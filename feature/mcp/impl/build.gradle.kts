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
        }
    }
}
