plugins {
    id("finsight.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.resources)

            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.compose.material3.adaptive)

            // Only `SettingsTileTheme` names it, and only to fill the two locals the
            // tiles read their look from. `implementation` is the point: the look is
            // shared, the rows are not, so a feature that renders tiles declares the
            // library itself.
            implementation(libs.compose.settings.ui.tiles)
        }
        commonTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
