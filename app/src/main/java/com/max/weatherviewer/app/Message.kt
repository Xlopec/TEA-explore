package com.max.weatherviewer.app

sealed class Message

sealed class Navigation : Message()

object NavigateToFeed : Navigation()

object NavigateToFavorite : Navigation()

object NavigateToTrending : Navigation()

object Pop : Navigation()

abstract class ScreenMessage : Message()