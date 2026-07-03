package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.InstallmentError

class InstallmentException(val error: InstallmentError) : Exception(error.message)