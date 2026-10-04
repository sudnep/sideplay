package io.github.jqssun.airplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.jqssun.airplay.service.AirPlayService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(Prefs.BOOT_AUTO_START, Prefs.DEF_BOOT_AUTO_START)
        if (!StartupBroadcast.shouldStart(intent.action, autoStart)) return

        val serviceIntent = Intent(context, AirPlayService::class.java)
            .setAction(AirPlayService.ACTION_START_SERVER)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
