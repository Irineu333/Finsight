package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.AccountError

class AccountException(val error: AccountError) : Exception(error.message)
