package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.SupportError

class SupportException(val error: SupportError) : Exception(error.message)
