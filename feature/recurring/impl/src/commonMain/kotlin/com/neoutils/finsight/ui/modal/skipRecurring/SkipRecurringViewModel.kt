package com.neoutils.finsight.ui.modal.skipRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.SkipRecurring
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.retire_action_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Skipping a cycle, from the sheet that asks first.
 *
 * The date is the one the confirmation was standing on, not today's: which month the
 * occurrence is filed under is the whole content of the decision, and re-deriving it
 * here would let the sheet skip a month other than the one the user was looking at.
 */
class SkipRecurringViewModel(
    private val recurring: Recurring,
    private val date: LocalDate,
    private val target: TransactionTarget,
    private val skipRecurringUseCase: SkipRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun skip() = viewModelScope.launch {
        skipRecurringUseCase(
            recurring = recurring,
            date = date,
        ).onLeft {
            crashlytics.recordException(it)
            modalManager.showError(UiText.Res(Res.string.retire_action_error_generic))
        }.onRight {
            analytics.logEvent(SkipRecurring(recurring, target))
            modalManager.dismissAll()
        }
    }
}
