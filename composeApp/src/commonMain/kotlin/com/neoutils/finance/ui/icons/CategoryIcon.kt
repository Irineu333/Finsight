package com.neoutils.finance.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class CategoryIcon(val key: String, val icon: ImageVector) {
    SHOPPING_CART("shopping_cart", Icons.Default.ShoppingCart),
    RESTAURANT("restaurant", Icons.Default.Restaurant),
    CAR("car", Icons.Default.DirectionsCar),
    HOME("home", Icons.Default.Home),
    FLIGHT("flight", Icons.Default.Flight),
    MOVIE("movie", Icons.Default.Movie),
    HAIRCUT("haircut", Icons.Default.ContentCut),
    FAVORITE("favorite", Icons.Default.Favorite),
    MEDICATION("medication", Icons.Default.Medication),
    BOOK("book", Icons.Default.MenuBook),
    BUILD("build", Icons.Default.Build),
    GAMES("games", Icons.Default.SportsEsports);

    companion object {
        fun fromKey(key: String): CategoryIcon {
            return entries.find { it.key == key } ?: SHOPPING_CART
        }
    }
}
