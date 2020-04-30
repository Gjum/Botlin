package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.Result
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException
import com.github.steveice10.mc.auth.exception.request.RequestException
import com.github.steveice10.mc.auth.service.AuthenticationService
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.google.gson.Gson
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.util.logging.Logger

const val mcUsernameEnv = "MINECRAFT_USERNAME"
const val mcPasswordEnv = "MINECRAFT_PASSWORD"

private class AuthTokenCache(val clientToken: String, val sessions: MutableMap<String, String> = mutableMapOf())

class AuthenticationProvider(
	private val username: String,
	private val credentialsPath: String,
	private val authCachePath: String
) {
	private val logger = Logger.getLogger(this::class.java.name)

	// TODO causes race condition when used from multiple processes, because tokens for all accounts are cached in the same file

	suspend fun authenticate(): Result<MinecraftProtocol, RequestException> {
		logger.fine("Authenticating $username")
		var cToken: String = UUID.randomUUID().toString() // TODO allow providing default value

		// try loading cached token, and maybe client token
		val authCache = readAuthTokenCache(authCachePath)
		if (authCache == null) {
			logger.fine("Could not read auth cache from $authCachePath")
		} else {
			cToken = authCache.clientToken
			val aToken = authCache.sessions[username]
			if (aToken == null || aToken.isEmpty()) {
				logger.fine("Could not read auth token from $authCachePath")
			} else {
				try {
					val auth = AuthenticationService(cToken)
					auth.username = username
					auth.accessToken = aToken
					return tryLoginAndCacheToken(auth)
				} catch (e: InvalidCredentialsException) {
					logger.info("Could not reuse auth token from $authCachePath")
				}
			}
		}

		// try password from env
		val envUsername = System.getenv(mcUsernameEnv) ?: ""
		val envPassword = System.getenv(mcPasswordEnv) ?: ""
		if (envPassword.isNotEmpty() && username == envUsername) {
			try {
				val auth = AuthenticationService(cToken)
				auth.username = username
				auth.password = envPassword
				return tryLoginAndCacheToken(auth)
			} catch (e: InvalidCredentialsException) {
				logger.warning("Invalid password provided via env ($mcPasswordEnv)")
			}
		}

		if ('@' !in username) { // fall back to offline mode
			logger.info("Selecting offline mode with username `$username`")
			return Result.Success(MinecraftProtocol(username))
		}

		// try password from .credentials
		val credentials = readCredentialsFile(credentialsPath)
		if (credentials == null) {
			logger.info("Failed to authenticate $username: Could not read credentials from $credentialsPath and no password provided via env ($mcPasswordEnv)")

			// create example .credentials file so the user can add their credentials
			Files.write(Paths.get(credentialsPath), "your-mojang-account@example.com YOUR PASSWORD\n".toByteArray())

			return Result.Failure(RequestException("Failed to authenticate $username: Could not read credentials from $credentialsPath and no password provided via env ($mcPasswordEnv)"))
		}

		val credPassword = credentials[username] ?: ""
		if (credPassword.isEmpty()) {
			logger.info("Failed to authenticate $username: No password provided via env ($mcPasswordEnv) or $credentialsPath")
			return Result.Failure(RequestException("Failed to authenticate $username: No password provided via env ($mcPasswordEnv) or $credentialsPath"))
		}

		return try {
			val auth = AuthenticationService(cToken)
			auth.username = username
			auth.password = credPassword
			tryLoginAndCacheToken(auth)
		} catch (e: InvalidCredentialsException) {
			logger.warning("Failed to authenticate $username: Invalid password provided via $credentialsPath")
			Result.Failure(RequestException("Failed to authenticate $username: Invalid password provided via $credentialsPath"))
		}
	}

	private suspend fun tryLoginAndCacheToken(auth: AuthenticationService): Result<MinecraftProtocol, RequestException> {
		val result = tryLogin(auth)
		if (result.value != null) cacheToken(authCachePath, auth)
		return result
	}

	private suspend fun tryLogin(auth: AuthenticationService): Result<MinecraftProtocol, RequestException> {
		if (auth.accessToken == null && auth.password != null && auth.username != null) {
			logger.info("Logging into `${auth.username}` with password")
		}
		val error = runOnThread {
			try {
				auth.login()
				null
			} catch (e: RequestException) {
				e
			}
		}
		if (error != null) return Result.Failure(error)
		return Result.Success(mcProtoFromAuth(auth))
	}

	private fun cacheToken(authCachePath: String, auth: AuthenticationService, authCacheArg: AuthTokenCache? = null) {
		val authCache = authCacheArg ?: AuthTokenCache(auth.clientToken)
		authCache.sessions[auth.username] = auth.accessToken
		try {
			Files.write(Paths.get(authCachePath), Gson().toJson(authCache).toByteArray())
		} catch (e: IOException) {
			logger.warning("Could not write auth token cache to $authCachePath")
		}
	}
}

private fun readAuthTokenCache(authCachePath: String): AuthTokenCache? {
	val configStr: String
	try {
		// TODO do non-blocking read
		configStr = String(Files.readAllBytes(Paths.get(authCachePath)))
	} catch (e: IOException) {
		return null
	}
	if (configStr.isEmpty()) {
		return null
	}
	return Gson().fromJson(configStr, AuthTokenCache::class.java)
}

private fun readCredentialsFile(credentialsPath: String): Map<String, String>? {
	val credentialsLines: List<String>
	try {
		credentialsLines = Files.readAllLines(Paths.get(credentialsPath))
	} catch (e: IOException) {
		return null
	}
	if (credentialsLines.isEmpty()) {
		return null
	}
	return parseCredentialsFile(credentialsLines)
}

internal fun parseCredentialsFile(lines: List<String>): Map<String, String> {
	return lines
		.filter { line -> line.isNotEmpty() }
		.map { line ->
			val split = line.split(" ", limit = 2)
			split[0] to split[1]
		}.toMap()
}
