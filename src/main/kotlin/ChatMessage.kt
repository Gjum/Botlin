package com.github.gjum.minecraft.botlin

import com.github.steveice10.mc.protocol.data.message.ChatColor
import com.github.steveice10.mc.protocol.data.message.ChatFormat
import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.mc.protocol.data.message.TranslationMessage

fun Message.toPlain(): String {
    return toMotd().replace("\u00A7.".toRegex(), "")
}

fun Message.toAnsi(): String {
    var result = toMotd()
    for (fmt in Formatting.values()) {
        result = result.replace(fmt.motd, fmt.ansi)
    }
    return result
}

fun Message.toMotd(): String {
    val result = StringBuilder()
    if (style != null) {
        for (formats in style.formats) {
            when (formats) {
                ChatFormat.BOLD -> result.append(Formatting.BOLD.motd)
                ChatFormat.UNDERLINED -> result.append(Formatting.UNDERLINE.motd)
                ChatFormat.STRIKETHROUGH -> result.append(Formatting.STRIKETHROUGH.motd)
                ChatFormat.ITALIC -> result.append(Formatting.ITALIC.motd)
                ChatFormat.OBFUSCATED -> result.append(Formatting.OBFUSCATED.motd)
                null -> Unit
            }
        }
        when (style.color) {
            ChatColor.BLACK -> result.append(Formatting.BLACK.motd)
            ChatColor.DARK_BLUE -> result.append(Formatting.DARK_BLUE.motd)
            ChatColor.DARK_GREEN -> result.append(Formatting.DARK_GREEN.motd)
            ChatColor.DARK_AQUA -> result.append(Formatting.DARK_AQUA.motd)
            ChatColor.DARK_RED -> result.append(Formatting.DARK_RED.motd)
            ChatColor.DARK_PURPLE -> result.append(Formatting.DARK_PURPLE.motd)
            ChatColor.GOLD -> result.append(Formatting.GOLD.motd)
            ChatColor.GRAY -> result.append(Formatting.GRAY.motd)
            ChatColor.DARK_GRAY -> result.append(Formatting.DARK_GRAY.motd)
            ChatColor.BLUE -> result.append(Formatting.BLUE.motd)
            ChatColor.GREEN -> result.append(Formatting.GREEN.motd)
            ChatColor.AQUA -> result.append(Formatting.AQUA.motd)
            ChatColor.RED -> result.append(Formatting.RED.motd)
            ChatColor.LIGHT_PURPLE -> result.append(Formatting.LIGHT_PURPLE.motd)
            ChatColor.YELLOW -> result.append(Formatting.YELLOW.motd)
            ChatColor.WHITE -> result.append(Formatting.WHITE.motd)
            ChatColor.RESET -> result.append(Formatting.RESET.motd)
            null -> Unit
        }
    }
    result.append(
        when (this) {
            is TranslationMessage -> translate(translationKey, translationParams)
            else -> text
        }
    )
    if (extra.isNotEmpty()) {
        for (extra in extra) {
            result.append(extra.toMotd())
            result.append(Formatting.RESET.motd)
        }
    } else {
        result.append(Formatting.RESET.motd)
    }
    return result.toString()
}

private fun translate(key: String, params: Array<Message>): String {
    // TODO translate message
    val paramsStr = params.joinToString(" :: ") { message -> message.toMotd() }
    return "$key :: $paramsStr"
}

enum class Formatting(val motd: String, val ansi: String) {
    RESET("\u00A7r", "\u001B[0m"),
    BLACK("\u00A70", "\u001B[30m"),
    DARK_BLUE("\u00A71", "\u001B[34m"),
    DARK_GREEN("\u00A72", "\u001B[32m"),
    DARK_AQUA("\u00A73", "\u001B[36m"),
    DARK_RED("\u00A74", "\u001B[31m"),
    DARK_PURPLE("\u00A75", "\u001B[35m"),
    GOLD("\u00A76", "\u001B[33m"),
    GRAY("\u00A77", "\u001B[37m"),
    DARK_GRAY("\u00A78", "\u001B[90m"),
    BLUE("\u00A79", "\u001B[94m"),
    GREEN("\u00A7a", "\u001B[92m"),
    AQUA("\u00A7b", "\u001B[96m"),
    RED("\u00A7c", "\u001B[91m"),
    LIGHT_PURPLE("\u00A7d", "\u001B[95m"),
    YELLOW("\u00A7e", "\u001B[93m"),
    WHITE("\u00A7f", "\u001B[97m"),
    STRIKETHROUGH("\u00A7l", "\u001B[9m"),
    UNDERLINE("\u00A7o", "\u001B[4m"),
    BOLD("\u00A7n", "\u001B[1m"),
    ITALIC("\u00A7m", "\u001B[3m"),
    OBFUSCATED("\u00A7k", "\u001B[6m");
}
