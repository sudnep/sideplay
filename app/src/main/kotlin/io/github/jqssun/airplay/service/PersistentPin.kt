package io.github.jqssun.airplay.service

/** One pairing PIN per install: iPhones cache the code, so a new PIN per server run locks them out silently. */
object PersistentPin {
    data class Result(val pin: Int, val generated: Boolean)

    fun resolve(stored: Int, random: () -> Int): Result =
        if (stored in 0..9999) Result(stored, generated = false) else Result(random(), generated = true)

    fun format(pin: Int): String = "%04d".format(pin)
}
