package com.github.gjum.minecraft.botlin.cli

import com.github.gjum.minecraft.botlin.util.Formatting
import com.github.gjum.minecraft.botlin.util.Log
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
import java.util.Date
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.concurrent.thread

/**
 * Command-line interface.
 * Shows a command prompt and handles commands.
 * Overrides the logger to output above the prompt
 * and to format log messages with time and color according to log level.
 */
object Cli {
    private val logger = Logger.getLogger(this::class.java.name)

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
    // TODO return end reason: Ctrl+C, stop(), stdin/out closed, internal error
    fun run(commandHandler: (String) -> Unit) {
        try {
            start(commandHandler)
            join()
        } finally {
            stop()
        }
    }

    /**
     * Takes control of stdin/stderr,
     * printing any output above a persistent prompt
     * until [stop] is called.
     * Blocks until the CLI is started up.
     * @return [readThread]
     */
    fun start(commandHandler: (String) -> Unit): Thread {
        synchronized(this) {
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
            return readThread!!
        }
    }

    /**
     * Waits for the CLI to finish, by joining [readThread].
     */
    fun join() = readThread?.join()

    private fun startWriteThread(cliReader: LineReader, logReader: BufferedReader) = thread(start = true) {
        try {
            while (true) {
                val message = logReader.readLine() ?: continue
                cliReader.printAbove(message)
            }
        } catch (e: InterruptedIOException) {
            logger.log(Level.FINE, "Ending CLI log reader")
        } catch (e: Throwable) { // and rethrow
            logger.log(Level.SEVERE, "Error in CLI log reader: $e", e)
            throw e
        } finally {
            stop()
        }
    }

    private fun startReadThread(cliReader: LineReader, commandHandler: (String) -> Unit) = thread(start = true) {
        try {
            while (true) {
                val line = cliReader.readLine(prompt)
                commandHandler(line)
            }
        } catch (e: UserInterruptException) {
            logger.log(Level.FINE, "Ctrl+C pressed")
        } catch (e: EndOfFileException) {
            logger.log(Level.FINE, "StdIn closed")
        } catch (e: InterruptedException) {
            logger.log(Level.FINE, "Ending CLI command handler")
        } catch (e: Throwable) { // and rethrow
            logger.log(Level.SEVERE, "Error in CLI command handler: $e", e)
            throw e
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
        } catch (e: Throwable) { // and rethrow
            logger.log(Level.SEVERE, "Error while stopping CLI: $e", e)
            throw e
        }
    }
}

class CliFormatter : Formatter() {
    override fun format(record: LogRecord) = record.run {
        val timeStr = String.format("%tT", Date(millis))
        val levelStr = if (level == Level.INFO) "" else " $level"
        val thrownStr = thrown?.toString() ?: ""
        val error = if (message.endsWith(thrownStr)) "" else " - $thrownStr"
        "${levelColor()}[$timeStr$levelStr] $message$error$resetColor\n"
    }
}

private val resetColor = Formatting.RESET.ansi

private fun LogRecord.levelColor() = when (level) {
    Level.SEVERE -> Formatting.RED.ansi
    Level.WARNING -> Formatting.YELLOW.ansi
    Level.INFO -> ""
    Level.CONFIG -> Formatting.GRAY.ansi
    Level.FINE -> Formatting.GRAY.ansi
    Level.FINER -> Formatting.GRAY.ansi
    Level.FINEST -> Formatting.GRAY.ansi
    else -> ""
}
