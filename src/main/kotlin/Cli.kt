package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.Log.logger
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.utils.PumpReader
import org.jline.utils.WriterOutputStream
import java.io.BufferedReader
import java.io.InterruptedIOException
import java.io.PrintStream
import java.nio.charset.Charset
import java.util.*
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.concurrent.thread

object Cli {
    /**
     * may be changed on the fly
     */
    var prompt = "> "

    private var writeThread: Thread? = null
    private var readThread: Thread? = null
    private var sysOut: PrintStream? = null
    private var sysErr: PrintStream? = null

    /**
     * Takes control of stdin/stderr, printing any output above a persistent prompt.
     * Blocks until [stop] is called or an error occurs.
     */
    fun start(commandHandler: (String) -> Unit) {
        try {
            val readThread_ = synchronized(this) {
                // can't recover from this as commandHandler would be silently ignored
                if (writeThread != null) throw Error("CLI already started")

                val cliReader = LineReaderBuilder.builder().build()
                val pump = PumpReader()
                val logReader = BufferedReader(pump)
                val ps = PrintStream(WriterOutputStream(pump.writer, Charset.defaultCharset()))
                sysOut = System.out
                sysErr = System.err
                System.setOut(ps)
                System.setErr(ps)
                Log.reload()

                writeThread = startWriteThread(cliReader, logReader)
                readThread = startReadThread(cliReader, commandHandler)
                readThread!!
            }
            readThread_.join()
        } finally {
            stop()
        }
    }

    private fun startWriteThread(cliReader: LineReader, logReader: BufferedReader) = thread(start = true) {
        try {
            while (true) {
                val message = logReader.readLine() ?: continue
                cliReader.printAbove(message)
            }
        } catch (e: InterruptedIOException) {
            logger.log(Level.FINE, "Ending CLI log reader")
        } catch (e: Throwable) {
            logger.log(Level.SEVERE, "Error in CLI log reader: $e", e)
        } finally {
            stop()
        }
    }

    private fun startReadThread(cliReader: LineReader, commandHandler: (String) -> Unit) = thread(start = true) {
        try {
            while (true) {
                val line = cliReader.readLine(prompt)
                try {
                    commandHandler(line)
                } catch (e: Throwable) {
                    logger.log(Level.WARNING, "CLI command handler threw exception: $e", e)
                }
            }
        } catch (e: UserInterruptException) {
            logger.log(Level.FINE, "Ctrl+C pressed")
        } catch (e: EndOfFileException) {
            logger.log(Level.FINE, "StdIn closed")
        } catch (e: InterruptedException) {
            logger.log(Level.FINE, "Ending CLI command handler")
        } catch (e: Throwable) {
            logger.log(Level.SEVERE, "Error in CLI command handler: $e", e)
        } finally {
            stop()
        }
    }

    fun stop() {
        try {
            synchronized(this) {
                if (writeThread == null && readThread == null) {
                    logger.fine("CLI already shut down")
                    return
                }
                logger.log(Level.FINE, "CLI shutting down")
                System.out.flush()
                System.err.flush()
                System.setOut(sysOut)
                System.setErr(sysErr)
                Log.reload()
                writeThread?.interrupt()
                writeThread = null
                readThread?.interrupt()
                readThread = null
                if (prompt != "") println() // end the partial command line
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error while stopping CLI: $e", e)
        }
    }
}

class CliFormatter : Formatter() {
    private val dat = Date() // reuse instance for performance

    override fun format(record: LogRecord) = record.run {
        dat.time = millis
        val timeStr = String.format("%tT", dat)
        val error = thrown?.run { " - $thrown" } ?: ""
        "[$timeStr $level] $message$error\n"
    }
}
