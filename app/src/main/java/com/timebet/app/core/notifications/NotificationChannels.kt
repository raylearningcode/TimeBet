package com.timebet.app.core.notifications

object NotificationChannels {
    const val LOW_TIME = "timebet_low_time"
    const val BLOCKING = "timebet_blocking"
    const val SPORTS = "timebet_sports"
}

object NotificationIds {
    const val LOW_TIME_WARNING = 1001
    const val TIME_UP = 1002
    const val TRACKING_FAILURE = 1003
    const val SPORTS_SETTLEMENT = 2001
    const val FOREGROUND_SERVICE = 3001
}
