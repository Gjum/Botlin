package com.github.gjum.minecraft.botlin

import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import java.util.logging.Formatter
import java.util.logging.LogManager
import java.util.logging.LogRecord
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

class LogFormatter : Formatter() {
    private val dat = Date() // reuse instance for performance

    override fun format(record: LogRecord) = record.run {
        dat.time = millis
        val timeStr = String.format("%tFT%1\$tT", dat)
        val source = sourceClassName?.run { "$sourceClassName $sourceMethodName" } ?: loggerName
        val trace = thrown?.stringifyStackTrace() ?: ""
        "[$timeStr $level] $message (in $source)$trace\n"
    }
}

fun Throwable.stringifyStackTrace(): String {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    pw.println()
    printStackTrace(pw)
    pw.close()
    return sw.toString()
}
