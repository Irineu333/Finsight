package com.neoutils.finance.domain.model.form

import com.neoutils.finance.util.FieldForm
import com.neoutils.finance.util.Validation

data class AddCreditForm(
    val name: FieldForm = FieldForm(),
    val limit: String = "",
    val closingDay: String = "",
    val dueDay: String = "",
    val closingDayCalc: Int? = null,
    val dueDayCalc: Int? = null,
) {
    val effectiveClosingDay = closingDay.toIntOrNull() ?: closingDayCalc
    val effectiveDueDay = dueDay.toIntOrNull() ?: dueDayCalc

    fun isValid(): Boolean {
        return name.validation == Validation.Valid &&
                effectiveClosingDay != null &&
                effectiveDueDay != null
    }
}