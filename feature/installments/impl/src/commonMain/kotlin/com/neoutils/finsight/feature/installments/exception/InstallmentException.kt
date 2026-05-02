package com.neoutils.finsight.feature.installments.exception

import com.neoutils.finsight.feature.installments.error.InstallmentError

class InstallmentException(val error: InstallmentError) : Exception(error.message)
