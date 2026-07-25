package com.ultrastream.app.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Library : Screen("library")
    object Search : Screen("search")
    object Addons : Screen("addons")
    object Profile : Screen("profile")
    object Details : Screen("details/{id}/{type}") {
        fun pass(id: String, type: String) =
            "details/${URLEncoder.encode(id, "UTF-8")}/${URLEncoder.encode(type, "UTF-8")}"
    }
    object Player : Screen("player/{streamJson}/{title}") {
        fun pass(streamJson: String, title: String) =
            "player/${URLEncoder.encode(streamJson, "UTF-8")}/${URLEncoder.encode(title, "UTF-8")}"
    }
}

