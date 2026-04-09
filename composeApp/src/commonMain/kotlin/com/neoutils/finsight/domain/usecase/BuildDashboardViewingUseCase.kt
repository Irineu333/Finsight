package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.ui.screen.dashboard.DashboardComponentVariant
import com.neoutils.finsight.ui.screen.dashboard.DashboardComponentsBuilder
import com.neoutils.finsight.ui.screen.dashboard.DashboardComponentsInput
import com.neoutils.finsight.ui.screen.dashboard.toViewingVariant

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
