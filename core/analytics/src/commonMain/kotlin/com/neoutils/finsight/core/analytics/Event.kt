package com.neoutils.finsight.core.analytics

abstract class Event(val name: String, val params: Map<String, String> = emptyMap())
