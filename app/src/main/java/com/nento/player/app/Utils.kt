package com.nento.player.app

object Utils {

    fun generateRandomId() : String {
        val dig1 = (1..9).random()
        val dig2 = (0..9).random()
        val dig3 = (0..9).random()
        val dig4 = (0..9).random()
        val dig5 = (0..9).random()
        val dig6 = (0..9).random()
        return "$dig1$dig2$dig3$dig4$dig5$dig6"
    }
}