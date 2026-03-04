package com.neoutils.finsight.util

object FeatureIconCatalog {

    val general: List<CategoryIcon> = listOf(
        CategoryIcon.DEFAULT,
        CategoryIcon.FAVORITE,
        CategoryIcon.MONEY,
        CategoryIcon.BUSINESS,
        CategoryIcon.WORK,
        CategoryIcon.GIFT,
        CategoryIcon.CELEBRATION,
    )

    val categories: List<CategoryIcon> = listOf(
        CategoryIcon.SHOPPING_CART,
        CategoryIcon.RESTAURANT,
        CategoryIcon.FASTFOOD,
        CategoryIcon.PIZZA,
        CategoryIcon.COFFEE,
        CategoryIcon.HOME,
        CategoryIcon.CAR,
        CategoryIcon.BUS,
        CategoryIcon.TRAIN,
        CategoryIcon.BIKE,
        CategoryIcon.WALK,
        CategoryIcon.FLIGHT,
        CategoryIcon.HEALTH,
        CategoryIcon.MEDICATION,
        CategoryIcon.FITNESS,
        CategoryIcon.SPA,
        CategoryIcon.SCHOOL,
        CategoryIcon.BOOK,
        CategoryIcon.MOVIE,
        CategoryIcon.MUSIC,
        CategoryIcon.THEATER,
        CategoryIcon.GAMES,
        CategoryIcon.BEACH,
        CategoryIcon.PARK,
        CategoryIcon.PETS,
        CategoryIcon.CHILD,
        CategoryIcon.PHONE,
        CategoryIcon.WIFI,
        CategoryIcon.POWER,
        CategoryIcon.WATER,
        CategoryIcon.BAR,
        CategoryIcon.BUILD,
        CategoryIcon.HAIRCUT,
    )

    val budgets: List<CategoryIcon> = listOf(
        CategoryIcon.SHOPPING_CART,
        CategoryIcon.RESTAURANT,
        CategoryIcon.HOME,
        CategoryIcon.CAR,
        CategoryIcon.FLIGHT,
        CategoryIcon.HEALTH,
        CategoryIcon.SCHOOL,
        CategoryIcon.MOVIE,
        CategoryIcon.WIFI,
        CategoryIcon.PHONE,
        CategoryIcon.WATER,
        CategoryIcon.POWER,
        CategoryIcon.MONEY,
    )

    val accounts: List<CategoryIcon> = listOf(
        CategoryIcon.MONEY,
        CategoryIcon.BUSINESS,
        CategoryIcon.WORK,
        CategoryIcon.HOME,
        CategoryIcon.PHONE,
        CategoryIcon.WIFI,
        CategoryIcon.WATER,
        CategoryIcon.POWER,
        CategoryIcon.FAVORITE,
    )

    fun withGeneral(
        featureIcons: List<CategoryIcon>,
        selectedIcon: CategoryIcon? = null,
    ): List<CategoryIcon> {
        val icons = (featureIcons + general).distinctBy(CategoryIcon::key)
        return if (selectedIcon == null || icons.any { it.key == selectedIcon.key }) {
            icons
        } else {
            listOf(selectedIcon) + icons
        }
    }
}
