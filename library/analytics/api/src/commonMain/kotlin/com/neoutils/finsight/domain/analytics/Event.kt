package com.neoutils.finsight.domain.analytics

abstract class Event(val name: String, val params: Map<String, String> = emptyMap())
