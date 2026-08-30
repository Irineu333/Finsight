plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `PreventiveCaptureException` hands the caller the sentence to show, because
            // the error type it comes from names refusals of `:core:database` and stays in
            // this feature's `impl`. `api`, not `implementation`: `UiText` is part of the
            // signature every caller reads.
            api(projects.core.common)
            api(projects.core.navigation)

            // The two sheets a destructive confirmation puts up — the refusal a failed
            // capture raises, and the offer to turn the vault on — are this module's
            // because their rule is: five confirmations across three features show them,
            // and `impl ⊄ impl` leaves the api of the feature that owns the rule as the
            // one place all five can reach. `api`, not `implementation`:
            // `ProceedWithoutCopyModal` is a `ModalBottomSheet` and every caller reads
            // that supertype.
            api(projects.core.designsystem)
            implementation(projects.core.resources)
        }
    }
}
