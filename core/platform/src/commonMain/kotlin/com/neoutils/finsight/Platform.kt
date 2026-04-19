package com.neoutils.finsight

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect val isDesktop: Boolean