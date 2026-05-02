package com.neoutils.finsight.feature.accounts.exception

import com.neoutils.finsight.feature.accounts.error.AccountError
class AccountException(val error: AccountError) : Exception(error.message)
