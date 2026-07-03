package com.neoutils.finsight.domain.exception

class InvoiceNotAdjustedException : Exception("Invoice balance unchanged — target matches current value")