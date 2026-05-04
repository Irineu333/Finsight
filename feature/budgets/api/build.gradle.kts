plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {

            api(projects.feature.categories.api)

            api(projects.feature.recurring.api)

            api(projects.feature.transactions.api)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
            implementation(projects.core.ui)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.budgets.api"
}
