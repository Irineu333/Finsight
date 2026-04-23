package com.neoutils.finsight.extension

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.UIKit.UIViewController

internal fun UIViewController.resolvePresenter(): UIViewController {
    val windowRoot = view.window?.rootViewController
    return (windowRoot ?: this).topMostPresented()
}

internal fun UIViewController.topMostPresented(): UIViewController {
    var current = this
    while (true) {
        val presented = current.presentedViewController ?: return current
        current = presented
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun UIView.popoverCenterRect(): CValue<CGRect> {
    val width = bounds.useContents { size.width }
    val height = bounds.useContents { size.height }
    return CGRectMake(width / 2.0, height / 2.0, 1.0, 1.0)
}
