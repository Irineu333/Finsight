package com.neoutils.finsight.util

object FeatureIconCatalog {

    val general: List<AppIcon> = listOf(
        AppIcon.DEFAULT,
        AppIcon.FAVORITE,
        AppIcon.CATEGORY,
        AppIcon.MONEY,
        AppIcon.WALLET,
        AppIcon.BANK,
        AppIcon.CARD,
        AppIcon.SAVINGS,
        AppIcon.BUSINESS,
        AppIcon.WORK,
        AppIcon.GIFT,
        AppIcon.CELEBRATION,
        AppIcon.RECEIPT,
        AppIcon.PAYMENT,
    )

    val categories: List<AppIcon> = listOf(
        AppIcon.CATEGORY,
        AppIcon.MONEY,
        AppIcon.WORK,
        AppIcon.BUSINESS,
        AppIcon.GIFT,
        AppIcon.CELEBRATION,
        AppIcon.SAVINGS,
        AppIcon.PAYMENT,
        AppIcon.SHOPPING_CART,
        AppIcon.GROCERY,
        AppIcon.RESTAURANT,
        AppIcon.FASTFOOD,
        AppIcon.COFFEE,
        AppIcon.BAR,
        AppIcon.HOME,
        AppIcon.CAR,
        AppIcon.BUS,
        AppIcon.TRAIN,
        AppIcon.BIKE,
        AppIcon.WALK,
        AppIcon.RUN,
        AppIcon.GAS,
        AppIcon.FLIGHT,
        AppIcon.HEALTH,
        AppIcon.MEDICATION,
        AppIcon.FITNESS,
        AppIcon.SCHOOL,
        AppIcon.BOOK,
        AppIcon.MOVIE,
        AppIcon.MUSIC,
        AppIcon.THEATER,
        AppIcon.GAMES,
        AppIcon.PETS,
        AppIcon.CHILD,
        AppIcon.CLOTHES,
        AppIcon.REPAIR,
        AppIcon.PHONE,
        AppIcon.WIFI,
        AppIcon.POWER,
        AppIcon.WATER,
        AppIcon.BUILD,
        AppIcon.HAIRCUT,
        AppIcon.BEACH,
        AppIcon.PARK,
    )

    val budgets: List<AppIcon> = listOf(
        AppIcon.BUDGET,
        AppIcon.MONEY,
        AppIcon.SAVINGS,
        AppIcon.WALLET,
        AppIcon.RECEIPT,
        AppIcon.PAYMENT,
        AppIcon.SHOPPING_CART,
        AppIcon.GROCERY,
        AppIcon.RESTAURANT,
        AppIcon.HOME,
        AppIcon.CAR,
        AppIcon.GAS,
        AppIcon.FLIGHT,
        AppIcon.HEALTH,
        AppIcon.SCHOOL,
        AppIcon.PHONE,
        AppIcon.WIFI,
        AppIcon.WATER,
        AppIcon.POWER,
        AppIcon.MOVIE,
        AppIcon.PETS,
    )

    val accounts: List<AppIcon> = listOf(
        AppIcon.WALLET,
        AppIcon.BANK,
        AppIcon.CARD,
        AppIcon.MONEY,
        AppIcon.SAVINGS,
        AppIcon.PAYMENT,
        AppIcon.BUSINESS,
        AppIcon.WORK,
        AppIcon.PHONE,
        AppIcon.SMARTPHONE,
        AppIcon.FAVORITE,
    )

    val creditCards: List<AppIcon> = listOf(
        AppIcon.CARD,
        AppIcon.PAYMENT,
        AppIcon.RECEIPT,
        AppIcon.MONEY,
        AppIcon.WALLET,
        AppIcon.BANK,
        AppIcon.SAVINGS,
        AppIcon.BUSINESS,
        AppIcon.FAVORITE,
    )

    fun withGeneral(
        featureIcons: List<AppIcon>,
        selectedIcon: AppIcon? = null,
    ): List<AppIcon> {
        val icons = (featureIcons + general).distinctBy(AppIcon::key)
        return if (selectedIcon == null || icons.any { it.key == selectedIcon.key }) {
            icons
        } else {
            listOf(selectedIcon) + icons
        }
    }
}
