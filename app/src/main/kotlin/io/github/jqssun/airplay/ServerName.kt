package io.github.jqssun.airplay

import android.content.Context
import android.os.Build
import android.provider.Settings

/** Default receiver name: the TV's own name, so the iPhone shows e.g. "Living Room TV". */
object ServerName {
    const val FALLBACK = "Sideplay"

    // RAOP is advertised as "<12 hex MAC>@<name>"; mDNS labels max out at 63 bytes
    const val MAX_BYTES = 50

    fun choose(deviceName: String?, manufacturer: String?, model: String?): String {
        val device = deviceName?.trim().orEmpty()
        if (device.isNotEmpty()) return truncate(device)
        val mfr = manufacturer?.trim().orEmpty()
        val mdl = model?.trim().orEmpty()
        val hw = when {
            mdl.isEmpty() -> mfr
            mfr.isEmpty() || mdl.startsWith(mfr, ignoreCase = true) -> mdl
            else -> "$mfr $mdl"
        }
        return if (hw.isEmpty()) FALLBACK else truncate(hw)
    }

    fun default(context: Context): String {
        val deviceName = if (Build.VERSION.SDK_INT >= 25)
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) else null
        return choose(deviceName, Build.MANUFACTURER, Build.MODEL)
    }

    private fun truncate(name: String): String {
        if (name.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) return name
        val out = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            val chunk = String(Character.toChars(cp))
            val n = chunk.toByteArray(Charsets.UTF_8).size
            if (bytes + n > MAX_BYTES) break
            out.append(chunk)
            bytes += n
            i += Character.charCount(cp)
        }
        return out.toString().trimEnd()
    }
}
