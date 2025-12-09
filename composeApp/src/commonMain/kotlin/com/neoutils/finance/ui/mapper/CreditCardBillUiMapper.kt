package com.neoutils.finance.ui.mapper

import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.model.CreditCardBillUi

class CreditCardBillUiMapper {

    fun toUi(
        bill: Double,
        limit: Double
    ): CreditCardBillUi {

        val displayBill = bill.coerceAtLeast(0.0)

        val availableLimit = (limit - bill).coerceAtLeast(0.0)

        if (displayBill > 0 && limit > 0) {
            return CreditCardBillUi(
                bill = displayBill.toMoneyFormat(),
                limit = limit.toMoneyFormat(),
                availableLimit = availableLimit.toMoneyFormat(),
                usagePercentage = (bill / limit).coerceIn(0.0, 1.0),
                showProgress = true,
            )
        }

        return CreditCardBillUi(
            bill = displayBill.toMoneyFormat(),
            limit = limit.toMoneyFormat(),
            availableLimit = availableLimit.toMoneyFormat(),
            usagePercentage = 0.0,
            showProgress = false
        )
    }
}
