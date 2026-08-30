plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.ledger)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.designsystem)
            implementation(projects.core.ui)
            implementation(projects.core.resources)
            implementation(projects.core.analytics)
            implementation(projects.core.crashlytics)

            implementation(projects.feature.creditcards.api)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.recurring.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.accounts.api)

            // Two sheets a deletion here puts up come from there: the refusal a failed
            // preventive capture raises, and the offer to turn the vault on beside the
            // largest thing this app destroys on one confirmation. Both are one rule with
            // one owner, and it is the feature that owns the vault. The api, never the
            // impl, which the dependency rules would refuse anyway.
            implementation(projects.feature.backup.api)

            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.turbine)
        }
    }
}
