package com.neoutils.finance.domain.exception

import com.neoutils.finance.domain.error.AccountError

class AccountException(val error: AccountError) : Exception(error.message)