package com.neoutils.finance

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform