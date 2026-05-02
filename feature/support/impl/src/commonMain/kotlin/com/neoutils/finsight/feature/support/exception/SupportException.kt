package com.neoutils.finsight.feature.support.exception

import com.neoutils.finsight.feature.support.error.SupportError
class SupportException(val error: SupportError) : Exception(error.message)
