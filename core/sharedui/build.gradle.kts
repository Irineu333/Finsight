// :core:sharedui — transitional bucket for cross-feature Compose components and modais
// that consume domain types from multiple :feature:X:api modules. Lives under :core
// because it must be reachable from every :feature:X:impl, but it deliberately
// breaches D10 by depending on :feature:X:api modules. Once §18 lands (Transaction/Operation
// referencing IDs instead of bundled domain objects), most of these widgets can move
// back into their owning feature:impl and this module dies.
plugins {
    id("kmp-compose")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.domain)
            implementation(projects.core.utils)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.creditCards.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.budgets.api)
            implementation(libs.kotlinx.datetime)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.core.sharedui.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.core.sharedui"
}
