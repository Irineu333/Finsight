package com.neoutils.finsight

enum class Platform { Android, Desktop, IOS }

expect val currentPlatform: Platform

val isDesktop: Boolean get() = currentPlatform == Platform.Desktop
