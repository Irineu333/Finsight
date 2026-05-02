package com.neoutils.finsight.feature.accounts.exception

class AccountNotAdjustedException : Exception("Account balance unchanged — target matches current value")