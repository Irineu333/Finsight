package com.neoutils.finsight.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.twotone.Label
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppIcon(
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
    GROCERY("grocery", Icons.Default.LocalGroceryStore),
    SHOPPING_BAG("shopping_bag", Icons.Default.ShoppingBag),
    STORE("store", Icons.Default.Storefront),
    BAR("bar", Icons.Default.LocalBar),

    BUS("bus", Icons.Default.DirectionsBus),
    TRAIN("train", Icons.Default.Train),
    BIKE("bike", Icons.AutoMirrored.Filled.DirectionsBike),
    WALK("walk", Icons.AutoMirrored.Filled.DirectionsWalk),
    RUN("run", Icons.AutoMirrored.Filled.DirectionsRun),
    GAS("gas", Icons.Default.LocalGasStation),

    HEALTH("health", Icons.Default.LocalHospital),
    FITNESS("fitness", Icons.Default.FitnessCenter),
    SPA("spa", Icons.Default.Spa),

    MUSIC("music", Icons.Default.MusicNote),
    THEATER("theater", Icons.Default.TheaterComedy),
    BEACH("beach", Icons.Default.BeachAccess),
    PARK("park", Icons.Default.Park),
    SOCCER("soccer", Icons.Default.SportsSoccer),
    BASKETBALL("basketball", Icons.Default.SportsBasketball),

    PHONE("phone", Icons.Default.Phone),
    SMARTPHONE("smartphone", Icons.Default.Smartphone),
    LAPTOP("laptop", Icons.Default.LaptopMac),
    TV("tv", Icons.Default.Tv),
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
    CLOTHES("clothes", Icons.Default.Checkroom),
    REPAIR("repair", Icons.Default.Handyman),
    DEFAULT("default", Icons.AutoMirrored.TwoTone.Label),

    CATEGORY("category", Icons.Default.Category),
    BUDGET("budget", Icons.Default.PieChart),
    GOAL("goal", Icons.AutoMirrored.Filled.TrendingUp),
    WALLET("wallet", Icons.Default.AccountBalanceWallet),
    BANK("bank", Icons.Default.AccountBalance),
    CARD("card", Icons.Default.CreditCard),
    SAVINGS("savings", Icons.Default.Savings),
    RECEIPT("receipt", Icons.AutoMirrored.Filled.ReceiptLong),
    PAYMENT("payment", Icons.Default.Payments);

    companion object {
        fun fromKey(key: String) = entries.find { it.key == key } ?: DEFAULT
    }
}
