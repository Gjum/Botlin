package com.github.gjum.minecraft.botlin.cli

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.data.ItemInfo
import com.github.gjum.minecraft.botlin.util.toAnsi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.regex.Pattern

fun registerUsefulCommands(commands: CommandRegistry, bot: Bot, parentScope: CoroutineScope) {
	commands.registerCommand("quit", "Close the program.", listOf("exit", "close")
	) { _, _ ->
		bot.disconnect("Closing the program")
		parentScope.coroutineContext.cancel()
	}
	commands.registerCommand("connect <address>", "Connect to server.", listOf("login")
	) { cmdLine, _ ->
		// TODO bail if already connected, before authenticating - Mojang doesn't allow multiple sessions with same token
		parentScope.launch { bot.connect(cmdLine.split(' ')[1]) }
	}
	commands.registerCommand("disconnect", "Disconnect from server.", listOf("logout", "logoff")
	) { _, _ ->
		bot.disconnect("CLI disconnect command")
	}
	commands.registerCommand("respawn", "Respawn if dead."
	) { _, context ->
		if (!bot.alive) context.respond("Already alive")
		else parentScope.launch { bot.respawn() }
	}
	commands.registerCommand("info", "Show bot info: connection, location, health, etc.", listOf("status", "state")
	) { _, context ->
		bot.playerEntity?.run {
			context.respond("${bot.profile.name} at $position $look on ${bot.serverAddress}")
			context.respond("health=$health food=$food sat=$saturation exp=${experience?.total} (${experience?.level} lvl)")
		} ?: context.respond("${bot.profile.name} (not spawned)")
	}
	commands.registerCommand("inventory", "List all items in the current window.", listOf("inv", "showinv")
	) { _, context ->
		bot.window?.run {
			val slotsLines = slots
				.groupBy { it as ItemInfo }
				.mapValues { (_, slots) -> slots.sumBy { it.amount } }
				.asIterable()
				.joinToString("\n") { (i, n) ->
					val numPadded = n.toString().padStart(4)
					"$numPadded ${i.displayName}"
				}
			context.respond("Inventory:\n$slotsLines")
		} ?: context.respond("No window open")
	}
	commands.registerCommand("players", "Show connected players list.", listOf("online", "list")
	) { _, context ->
		val namesSpaceSep = bot.playerList.values
			.sortedBy { it.profile.name }
			.joinToString(" ") {
				it.displayName?.toAnsi() ?: it.profile.name
			}
		context.respond("Connected players: $namesSpaceSep")
	}
	commands.registerCommand("map", "Show surrounding terrain.", listOf("nearby", "blocks", "terrain")
	) { _, context ->
		val blocksWithDY = bot.world!!.run {
			val feetY = bot.feet.y.floor
			fun getBlockRel(delta: Vec3i) = getBlockState(bot.feet.floored() + delta)
			(-3..3).map { dz ->
				(-3..3).map { dx ->
					var dy = 0 // start at bot feet
					var block = getBlockRel(Vec3i(dx, dy, dz))
					// find first empty block above player feet
					while (block != null && block.id != 0) {
						block = getBlockRel(Vec3i(dx, ++dy, dz))
					}
					// find highest non-empty block from there
					while ((block == null || block.id == 0)
						&& feetY + dy >= 0) {
						block = getBlockRel(Vec3i(dx, --dy, dz))
					}
					Pair(block, dy + 1) // +1 so floor is 0, as it is -1 from feet
				}
			}
		}
		val blockKey = blocksWithDY.asSequence().flatten().map { it.first }
			.distinct()
			.filterNotNull()
			.sortedBy { it.id }
			.joinToString("\n") {
				val displayName = bot.mcData.getBlockStateInfo(it)?.block?.displayName
					?: error("Unknown block id $it")
				val idPadded = it.id.toString().padStart(4)
				"$idPadded $displayName"
			}
		val dyKey = "${dy2ansi(-2)}--$ansiReset ${dy2ansi(-1)}-1$ansiReset ${dy2ansi(0)}0$ansiReset ${dy2ansi(1)}+1$ansiReset ${dy2ansi(2)}++$ansiReset"
		var i = 7 * 7 / 2 + 1 // index to mark as player pos
		val blocksText = blocksWithDY.joinToString("$ansiReset\n") { blocksInLine ->
			val blocksStr = blocksInLine.joinToString(ansiReset) { (block, dy) ->
				var padded = (block?.id?.toString() ?: "?").padStart(4)
				if (i == 1) padded = "[" + padded.substring(1)
				if (i == 0) padded = "]" + padded.substring(1)
				i--
				dy2ansi(dy) + padded
			}
		}
		context.respond("$dyKey\n$blockKey\n$blocksText")
	}
	commands.registerCommand("say <message>", "Send a chat message to the server.", listOf("chat", "talk")
	) { cmdLine, context ->
		val msg = cmdLine.substring("say ".length)
		if (bot.alive) parentScope.launch { bot.chat(msg) }
		else if (bot.connected) context.respond("Can't chat while dead")
		else context.respond("Not connected to any server")
	}
	// TODO hold item by name
	commands.registerCommand("hold <itemId> [itemMeta]", "Hold any matching item."
	) { cmdLine, context ->
		val args = cmdLine.split(' ')
		val (itemId, itemMeta) = try {
			Pair(args.getOrNull(1)?.toInt() ?: error("itemId is required"),
				args.getOrNull(2)?.toInt())
		} catch (e: Exception) {
			context.respond("Usage: $usage")
			return@registerCommand
		}
		parentScope.launch {
			bot.holdItem {
				it.itemId == itemId
					&& (itemMeta == null || itemMeta == it.meta)
			}
		}
	}

	val vec3dPattern = Pattern.compile(".*\\[?([-0-9.]+),? ([0-9.]+),? ([-0-9.]+)\\]?.*")
	fun Command.parseVec3dAndRun(cmdLine: String, context: CommandContext, block: (Vec3d) -> Unit) {
		val matcher = vec3dPattern.matcher(cmdLine)
		if (matcher.matches()) {
			val x = matcher.group(1)?.toDouble()
			val y = matcher.group(2)?.toDouble()
			val z = matcher.group(3)?.toDouble()
			if (x != null && y != null && z != null) {
				return block(Vec3d(x, y, z))
			}
		}
		context.respond("Usage: $usage")
	}

	commands.registerCommand("look <dx> <dy> <dz>", "Look in the direction of the given vector.", listOf("turn")
	) { cmdLine, context ->
		parseVec3dAndRun(cmdLine, context) { vec ->
			parentScope.launch {
				bot.lookVec(vec)
				context.respond("Done! ${bot.playerEntity?.look}")
			}
		}
	}
	commands.registerCommand("lookat <x> <y> <z>", "Look at the given coordinates.", listOf("face")
	) { cmdLine, context ->
		parseVec3dAndRun(cmdLine, context) { pos ->
			parentScope.launch {
				bot.lookAt(pos)
			}
		}
	}
	commands.registerCommand("move <dx> <dy> <dz>", "Move by the specified axis distances.", listOf("moveby")
	) { cmdLine, context ->
		parseVec3dAndRun(cmdLine, context) { vec ->
			parentScope.launch {
				bot.moveStraightBy(vec)
			}
		}
	}
	commands.registerCommand("go <x> <y> <z>", "Go to the given coordinates.", listOf("goto", "moveto")
	) { cmdLine, context ->
		parseVec3dAndRun(cmdLine, context) { pos ->
			parentScope.launch {
				bot.moveStraightTo(pos)
			}
		}
	}
	commands.registerCommand("jump", "Jump once."
	) { _, _ ->
		parentScope.launch { bot.jumpUntilLanded() }
	}
}

/** Build an ANSI escape sequence fro mthe given numbers. */
fun ansi(vararg n: Int) = n.joinToString(
	prefix = "\u001B[", separator = ";", postfix = "m")

val ansiReset = ansi(0)

/** Get the ANSI color sequence for the given difference in y level. */
fun dy2ansi(dy: Int) = when (dy.coerceAtMost(2)) {
	2 -> ansi(30, 48, 5, 255) // black on white
	1 -> ansi(30, 48, 5, 46) // black on light green
	0 -> ansi(97, 48, 5, 34) // white on green
	-1 -> ansi(97, 48, 5, 22) // white on dark green
	else -> ansi(97, 48, 5, 0) // white on black
}
