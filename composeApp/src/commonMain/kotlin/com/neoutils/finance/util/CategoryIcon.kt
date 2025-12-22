package com.neoutils.finance.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.twotone.Label
import androidx.compose.ui.graphics.vector.ImageVector

enum class CategoryIcon(
    val key: String,
    val icon: ImageVector
) {
    SHOPPING_CART("shopping_cart", Icons.Default.ShoppingCart),
    RESTAURANT("restaurant", Icons.Default.Restaurant),
    CAR("car", Icons.Default.DirectionsCar),
    HOME("home", Icons.Default.Home),
    FLIGHT("flight", Icons.Default.Flight),
    MOVIE("movie", Icons.Default.Movie),
    HAIRCUT("haircut", Icons.Default.ContentCut),
    FAVORITE("favorite", Icons.Default.Favorite),
    MEDICATION("medication", Icons.Default.Medication),
    BOOK("book", Icons.AutoMirrored.Filled.MenuBook),
    BUILD("build", Icons.Default.Build),
    GAMES("games", Icons.Default.SportsEsports),

    COFFEE("coffee", Icons.Default.LocalCafe),
    FASTFOOD("fastfood", Icons.Default.Fastfood),
    PIZZA("pizza", Icons.Default.LocalPizza),
    BAR("bar", Icons.Default.LocalBar),

    BUS("bus", Icons.Default.DirectionsBus),
    TRAIN("train", Icons.Default.Train),
    BIKE("bike", Icons.AutoMirrored.Filled.DirectionsBike),
    WALK("walk", Icons.AutoMirrored.Filled.DirectionsWalk),

    HEALTH("health", Icons.Default.LocalHospital),
    FITNESS("fitness", Icons.Default.FitnessCenter),
    SPA("spa", Icons.Default.Spa),

    MUSIC("music", Icons.Default.MusicNote),
    THEATER("theater", Icons.Default.TheaterComedy),
    BEACH("beach", Icons.Default.BeachAccess),
    PARK("park", Icons.Default.Park),

    PHONE("phone", Icons.Default.Phone),
    WIFI("wifi", Icons.Default.Wifi),
    POWER("power", Icons.Default.Power),
    WATER("water", Icons.Default.WaterDrop),

    SCHOOL("school", Icons.Default.School),
    WORK("work", Icons.Default.Work),
    BUSINESS("business", Icons.Default.BusinessCenter),
    MONEY("money", Icons.Default.AttachMoney),

    PETS("pets", Icons.Default.Pets),
    CHILD("child", Icons.Default.ChildCare),
    GIFT("gift", Icons.Default.CardGiftcard),
    CELEBRATION("celebration", Icons.Default.Cake),
    DEFAULT("default", Icons.TwoTone.Label);

    companion object {
        fun fromKey(key: String) = entries.find { it.key == key } ?: DEFAULT
    }
}