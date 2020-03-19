package com.github.gjum.minecraft.botlin.cli

import com.github.gjum.minecraft.botlin.api.Bot
import com.github.gjum.minecraft.botlin.api.Vec3d
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
	commands.registerCommand("info", "Show bot info: connection, location, health, etc.", listOf("status", "state")
	) { _, context ->
		bot.playerEntity?.run {
			context.respond("${bot.profile.name} at $position $look on ${bot.serverAddress}")
			context.respond("health=$health food=$food sat=$saturation exp=${experience?.total} (${experience?.level} lvl)")
		} ?: context.respond("${bot.profile.name} (not spawned)")
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
}
