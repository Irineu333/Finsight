plugins {
    id("finsight.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: `MoneyFigureText` and `BalanceCard` take a
            // `MoneyFigure`, so `:core:common` is part of this module's public surface.
            api(projects.core.common)
            implementation(projects.core.resources)

            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.compose.material3.adaptive)
        }
    }
}
