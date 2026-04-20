package com.neoutils.finsight.util

class CreditCardPeriod(
    private val defaultDaysDifference: Int = 8
) {
    fun calculateDueDay(closingDay: Int): Int {
        return ((closingDay - 1 + defaultDaysDifference) % 31) + 1
    }

    fun calculateClosingDay(dueDay: Int): Int {
        return ((dueDay - 1 - defaultDaysDifference + 31) % 31) + 1
    }
}
