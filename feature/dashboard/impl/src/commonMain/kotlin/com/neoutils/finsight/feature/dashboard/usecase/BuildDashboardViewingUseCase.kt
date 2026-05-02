package com.neoutils.finsight.feature.dashboard.usecase

import com.neoutils.finsight.feature.dashboard.model.DashboardComponentPreference
import com.neoutils.finsight.feature.dashboard.screen.DashboardComponentVariant
import com.neoutils.finsight.feature.dashboard.screen.DashboardComponentsBuilder
import com.neoutils.finsight.feature.dashboard.screen.DashboardComponentsInput
import com.neoutils.finsight.feature.dashboard.extension.toViewingVariant

class BuildDashboardViewingUseCase(
    private val builder: DashboardComponentsBuilder,
) {
    suspend operator fun invoke(
        input: DashboardComponentsInput,
        preferences: List<DashboardComponentPreference>,
    ): List<DashboardComponentVariant> {
        val context = builder.createContext(input)

        return preferences
            .sortedBy { it.position }
            .mapNotNull { pref ->
                val component = builder.build(
                    key = pref.key,
                    input = input,
                    context = context,
                    config = pref.config
                ) ?: return@mapNotNull null

                component.toViewingVariant(pref.config)
            }
    }
}
