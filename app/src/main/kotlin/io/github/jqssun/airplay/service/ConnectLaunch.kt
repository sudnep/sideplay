package io.github.jqssun.airplay.service

enum class ConnectLaunch { NONE, LAUNCH, PROMPT }

fun decideConnectLaunch(uiVisible: Boolean, canDrawOverlays: Boolean): ConnectLaunch = when {
    uiVisible -> ConnectLaunch.NONE
    canDrawOverlays -> ConnectLaunch.LAUNCH
    else -> ConnectLaunch.PROMPT
}
