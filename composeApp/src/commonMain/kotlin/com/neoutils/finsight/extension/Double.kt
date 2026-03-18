package com.neoutils.finsight.extension

import kotlin.math.roundToInt

fun Double.toPercentageString(): String {
    val rounded = ((this * 10).roundToInt() / 10.0).let { roundedValue ->
        if (roundedValue == -0.0) 0.0 else roundedValue
    }

    return "$rounded%"
}
