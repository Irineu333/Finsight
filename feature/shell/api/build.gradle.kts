plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.navigation)
            api(projects.core.designsystem)

            // `isDesktop`, which is the whole of the platform axis: a destination says which
            // platform its feature is restricted to, and the answer is read from here.
            api(projects.core.common)
        }
    }
}
