package io.github.jqssun.airplay.service

/**
 * The pairing PIN shown to the user. uxplay reuses one pin per server run, so a pin the ui has
 * dismissed must be forgotten here too, or a retry with the same code is swallowed as a repeat.
 */
class PinRelay(private val emit: (String?) -> Unit) {
    @Volatile var current: String? = null
        private set

    fun display(pin: String) {
        if (current == pin) return
        current = pin
        emit(pin)
    }

    fun clear() {
        current = null
        emit(null)
    }

    // the ui dismissed it: forget without echoing null back
    fun acknowledge() {
        current = null
    }
}
