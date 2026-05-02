package com.neoutils.finsight.feature.accounts.exception

import com.neoutils.finsight.feature.accounts.error.TransferError

class TransferException(val error: TransferError) : Exception(error.message)