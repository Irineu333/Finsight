package com.neoutils.finance.domain.exception

import com.neoutils.finance.domain.error.InstallmentError

class InstallmentException(val error: InstallmentError) : Exception(error.message)