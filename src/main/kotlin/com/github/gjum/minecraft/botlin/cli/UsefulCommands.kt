package com.github.gjum.minecraft.botlin.cli

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.util.toAnsi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.regex.Pattern

fun registerUsefulCommands(commands: CommandRegistry, bot: Bot, parentScope: CoroutineScope) {
	fun moveToWithJumpAndLook(dest: Vec3d, context: CommandContext) {
		parentScope.launch {
			bot.lookVec((dest - bot.feet).copy(y = 0.0))
			try {
				while (true) {
					val result = bot.moveStraightTo(dest)
					if (result is Result.Success) {
						break
					}
					// bumped into something. fall down then check if we can jump over it
					bot.playerEntity!!.apply {
						if (!onGround!!) {
							withTimeout(1000) {
								bot.receiveNext<AvatarEvent.PosLookSent> {
									onGround!!
								}
							}
							if (!onGround!!) { // timed out
								throw MoveError("Fell far down")
							}
						}
					}
					// jump over obstacle
					val smallDistance = (dest - bot.feet).copy(y = 0.0).normed() / 16.0
					bot.jumpByHeight(1.2)
					bot.moveStraightBy(smallDistance).getOrThrow()
				}
				context.respond("Arrived at ${bot.feet}")
			} catch (e: PhysicsError) {
				context.respond("Failed moving to $dest: $e at ${bot.feet}")
			}
		}
	}

	commands.registerCommand("quit", "Close the program.", listOf("exit", "close")
	) { _, _ ->
		bot.disconnect("Closing the program")
		parentScope.coroutineContext.cancel()
	}
	commands.registerCommand("connect <address>", "Connect to server.", listOf("login", "logon")
	) { cmdLine, _ ->
		val serverAddress = cmdLine.split(' ').getOrNull(1) ?: "localhost"
		// TODO bail if already connected, before authenticating - Mojang doesn't allow multiple sessions with same token
		parentScope.launch { bot.connect(serverAddress) }
	}
	commands.registerCommand("disconnect", "Disconnect from server.", listOf("logout", "logoff")
	) { _, _ ->
		bot.disconnect("CLI disconnect command")
	}
	commands.registerCommand("respawn", "Respawn if dead."
	) { _, context ->
		if (bot.alive) context.respond("Already alive")
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
				.groupBy { it.itemId }
				.filterKeys { it > 0 }
				.mapValues { (_, slots) -> slots.sumBy { it.amount } }
				.asIterable()
				.joinToString("\n") { (i, n) ->
					val displayName = bot.mcData.getItemInfo(i)?.displayName
					val numPadded = n.toString().padStart(4)
					"$numPadded $displayName"
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
			i += 7
			val yStr = blocksInLine.joinToString(ansiReset) { (block, dy) ->
				var padded = dy.toString().padStart(4)
				if (i == 1) padded = "[" + padded.substring(1)
				if (i == 0) padded = "]" + padded.substring(1)
				i--
				dy2ansiGray(dy) + padded
			}
			"$blocksStr$ansiReset    $yStr"
		}
		context.respond("$dyKey\n$blockKey\n$blocksText")
	}
	commands.registerCommand("say <message>", "Send a chat message to the server.", listOf("chat", "talk")
	) { cmdLine, context ->
		val msg = cmdLine.substringAfter(' ')
		if (bot.alive) parentScope.launch { bot.chat(msg) }
		else if (bot.connected) context.respond("Can't chat while dead")
		else context.respond("Not connected to any server")
	}
	// TODO hold/drop item by name
	commands.registerCommand("hold <itemId> [itemMeta]", "Hold any matching item."
	) cmd@{ cmdLine, context ->
		val args = cmdLine.split("[ :]+".toRegex())
		val (itemId, itemMeta) = try {
			Pair(args.getOrNull(1)?.toInt() ?: error("itemId is required"),
				args.getOrNull(2)?.toInt())
		} catch (e: Exception) {
			return@cmd context.respond("Usage: $usage")
		}
		parentScope.launch {
			bot.holdItem {
				it.itemId == itemId
					&& (itemMeta == null || itemMeta == it.meta)
			}
		}
	}
	commands.registerCommand("drop [amount | \"all\"] <itemId> [itemMeta]", "Drop matching items from the inventory."
	) cmd@{ cmdLine, context ->
		val args = cmdLine.split("[ :]+".toRegex())
		val (amount, itemId, itemMeta) = try {
			Triple(
				args.getOrNull(1)?.let {
					if (it == "all") Int.MAX_VALUE else it.toInt()
				} ?: error("amount is required"),
				args.getOrNull(2)?.toInt() ?: error("itemId is required"),
				args.getOrNull(3)?.toInt())
		} catch (e: Exception) {
			return@cmd context.respond("Usage: $usage")
		}
		parentScope.launch {
			var dropped = 0
			while (dropped < amount) {
				val leftToDrop = amount - dropped
				val slot = bot.findBestSlot {
					when {
						// ignore non-matching items
						it.itemId != itemId -> 0
						itemMeta != null && itemMeta != it.meta -> 0
						// prefer largest stack that can be fully dropped
						it.amount <= leftToDrop -> 64 + it.amount
						// prefer small stacks if no stacks can be fully dropped
						else -> 64 - it.amount
					}
				}
				if (slot == null) {
					if (amount != Int.MAX_VALUE) {
						context.respond("Could not drop $amount items, was ${amount - dropped} short.")
					}
					break
				}
				val taken = slot.amount.coerceAtMost(amount - dropped)
				if (taken == slot.amount) {
					bot.dropSlot(slot.index, fullStack = true)
					dropped += taken
				} else {
					while (!slot.empty && dropped < amount) {
						bot.dropSlot(slot.index, fullStack = false)
						dropped++
					}
				}
			}
			context.respond("Dropped $dropped items.")
		}
	}

	val vec3iPattern = Pattern.compile(".+ \\[? *([-0-9]+),? ([0-9]+),? ([-0-9]+)(?:$| .*)")
	val vec3dPattern = Pattern.compile(".+ \\[? *([-0-9.]+),? ([0-9.]+),? ([-0-9.]+)(?:$| .*)")
	val center = Vec3d(.5, .5, .5)
	fun Command.parseVec3dAndRun(cmdLine: String, context: CommandContext, intOffset: Vec3d = Vec3d.origin, block: (Vec3d) -> Unit) {
		// if all coords are integers, select center of block
		val matcherInt = vec3iPattern.matcher(cmdLine)
		if (matcherInt.matches()) {
			val x = matcherInt.group(1)?.toInt()
			val y = matcherInt.group(2)?.toInt()
			val z = matcherInt.group(3)?.toInt()
			if (x != null && y != null && z != null) {
				return block(Vec3i(x, y, z).asVec3d + intOffset)
			}
		}
		val matcherDouble = vec3dPattern.matcher(cmdLine)
		if (matcherDouble.matches()) {
			val x = matcherDouble.group(1)?.toDouble()
			val y = matcherDouble.group(2)?.toDouble()
			val z = matcherDouble.group(3)?.toDouble()
			if (x != null && y != null && z != null) {
				return block(Vec3d(x, y, z))
			}
		}
		context.respond("Usage: $usage")
	}

	commands.registerCommand("place <x> <y> <z>", "Place the currently held block onto that block.", listOf("build")
	) { cmdLine, context ->
		parseVec3dAndRun(cmdLine, context, center) { pos ->
			parentScope.launch {
				bot.placeBlock(pos)
			}
		}
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
		parseVec3dAndRun(cmdLine, context, center) { pos ->
			parentScope.launch {
				bot.lookAt(pos)
				context.respond("Done! ${bot.playerEntity?.look}")
			}
		}
	}
	commands.registerCommand("move <dx> <dy> <dz>", "Move by the specified axis distances.", listOf("moveby")
	) { cmdLine, context ->
		parseVec3dAndRun(cmdLine, context) { vec ->
			moveToWithJumpAndLook(vec + bot.feet, context)
		}
	}
	commands.registerCommand("go <x> <y> <z>", "Go to the given coordinates.", listOf("goto", "moveto")
	) { cmdLine, context ->
		parseVec3dAndRun(cmdLine, context, center) { pos ->
			moveToWithJumpAndLook(pos, context)
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

/** Get the grayscale ANSI color sequence for the given difference in y level. */
fun dy2ansiGray(dy: Int) = when {
	dy == 0 -> ansi(97, 48, 5, 34) // white on green
	dy > 0 -> ansi(30, 48, 5, (243 + dy).coerceAtMost(255)) // black on lighter grays
	dy < 0 -> ansi(97, 48, 5, (244 + dy).coerceAtLeast(232)) // white on darker grays
	else -> error("$dy is not less than, equal, or greater than 0")
}
