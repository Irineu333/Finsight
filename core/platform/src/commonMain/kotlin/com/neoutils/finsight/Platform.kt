package com.neoutils.finsight

enum class Platform {
    Android,
    Desktop,
    IOS;

    val isDesktop: Boolean get() = this == Desktop
}

expect val currentPlatform: Platform
