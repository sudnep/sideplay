package io.github.jqssun.airplay.ui.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Whether the service may bring the app to the front when a device connects (Android 10+ background start rules). */
enum class OverlayAccess { GRANTED, GRANTABLE, GUIDED, UNAVAILABLE }

fun resolveOverlayAccess(canDraw: Boolean, overlayScreen: Boolean, appDetailsScreen: Boolean): OverlayAccess = when {
    canDraw -> OverlayAccess.GRANTED
    overlayScreen -> OverlayAccess.GRANTABLE
    appDetailsScreen -> OverlayAccess.GUIDED
    else -> OverlayAccess.UNAVAILABLE
}

object OverlayPermission {
    private fun overlay(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
    // android 10 tvsettings has the page but no deep link; the app page sits one back-press from special app access
    private fun appDetails(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    fun access(ctx: Context): OverlayAccess {
        val pm = ctx.packageManager
        return resolveOverlayAccess(
            canDraw = Settings.canDrawOverlays(ctx),
            overlayScreen = overlay(ctx).resolveActivity(pm) != null,
            appDetailsScreen = appDetails(ctx).resolveActivity(pm) != null,
        )
    }

    fun intentFor(ctx: Context, access: OverlayAccess): Intent? = when (access) {
        OverlayAccess.GRANTABLE -> overlay(ctx)
        OverlayAccess.GUIDED -> appDetails(ctx)
        else -> null
    }
}
