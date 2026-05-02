package com.neoutils.finsight.core.platform

enum class Platform {
    Android,
    Desktop,
    IOS;

    val isDesktop: Boolean get() = this == Desktop
}

expect val currentPlatform: Platform
