package io.github.jqssun.airplay.ui.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Whether the service may bring the app to the front when a device connects (Android 10+ background start rules). */
enum class OverlayAccess { GRANTED, GRANTABLE, UNAVAILABLE }

fun resolveOverlayAccess(canDraw: Boolean, settingsScreenExists: Boolean): OverlayAccess = when {
    canDraw -> OverlayAccess.GRANTED
    settingsScreenExists -> OverlayAccess.GRANTABLE
    else -> OverlayAccess.UNAVAILABLE
}

object OverlayPermission {
    fun intent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun access(ctx: Context): OverlayAccess = resolveOverlayAccess(
        canDraw = Settings.canDrawOverlays(ctx),
        settingsScreenExists = intent(ctx).resolveActivity(ctx.packageManager) != null,
    )
}
