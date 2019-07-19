package com.github.gjum.minecraft.botlin.api

import com.github.steveice10.mc.auth.exception.request.RequestException
import com.github.steveice10.mc.protocol.MinecraftProtocol

/**
 * Starts connection attempt to [Avatar.serverAddress] as [Avatar.profile].
 * Does not wait for connection to succeed.
 */
suspend fun Avatar.connect(): RequestException? {
	val auth = serviceRegistry.consumeService(Authentication::class.java)!!
	val (proto, authError) = auth.authenticate()
	if (proto != null) useProtocol(proto)
	return authError
}

interface Authentication : Service {
	suspend fun authenticate(): Result<MinecraftProtocol, RequestException>
}
