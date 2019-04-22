package com.github.gjum.minecraft.botlin

import java.io.IOException
import java.util.logging.LogManager
import java.util.logging.Logger

object Log {
    init {
        reload()
    }

    fun reload() {
        val stream = Log::class.java.getResourceAsStream("/logging.properties")
        try {
            LogManager.getLogManager().readConfiguration(stream)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    val logger: Logger = Logger.getLogger(Log::class.java.name)
}
