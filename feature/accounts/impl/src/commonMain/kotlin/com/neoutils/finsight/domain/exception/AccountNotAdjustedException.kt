package com.neoutils.finsight.domain.exception

class AccountNotAdjustedException : Exception("Account balance unchanged — target matches current value")