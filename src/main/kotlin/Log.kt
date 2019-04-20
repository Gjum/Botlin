package com.github.gjum.minecraft.botlin

import java.util.logging.Level
import java.util.logging.Logger

object Log {
    // "-Djava.util.logging.SimpleFormatter.format=%1$tT %4$s: %5$s%6$s (in %2$s)%n"

    private val logger = Logger.getLogger(this::class.java.name)

    fun log(msg: String, level: Level = Level.INFO) {
        logger.log(level, msg)
//        ZonedDateTime.now(UTC).run {
//            println(String.format("%02d:%02d:%02d %s", hour, minute, second, msg))
//        }
    }
}
