package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Authentication
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException
import com.github.steveice10.mc.auth.service.AuthenticationService
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.google.gson.Gson
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.util.logging.Logger

const val mcPasswordEnv = "MINECRAFT_PASSWORD"

private class AuthTokenCache(val clientToken: String, val sessions: MutableMap<String, String> = mutableMapOf())

class AuthenticationProvider(
    private val credentialsPath: String,
    private val authCachePath: String
) : Authentication {
    private val logger: Logger = Logger.getLogger(this::class.java.name)

    override val defaultAccount: String
        get () {
            return System.getProperty("mcUsername")
                ?: System.getenv("MINECRAFT_USERNAME")
                ?: "" // TODO check authCache and .credentials
        }

    override val availableAccounts: Collection<String>
        get() {
            TODO("check authCache and .credentials")
        }

    override fun authenticate(username: String): MinecraftProtocol? {
        var cToken: String = UUID.randomUUID().toString() // TODO allow providing default value

        // try loading cached token, and maybe client token
        val authCache = readAuthTokenCache(authCachePath)
        if (authCache == null) {
            logger.fine("Could not read auth cache from $authCachePath")
        } else {
            cToken = authCache.clientToken
            val aToken = authCache.sessions[username]
            if (aToken == null || aToken.isEmpty()) {
                logger.info("Could not read auth token from $authCachePath")
            } else {
                try {
                    val auth = AuthenticationService(cToken)
                    auth.username = username
                    auth.accessToken = aToken
                    auth.login()
                    // success!
                    cacheToken(authCachePath, auth, authCache)
                    return mcProtoFromAuth(auth)
                } catch (e: InvalidCredentialsException) {
                    logger.info("Could not reuse auth token from $authCachePath")
                }
            }
        }

        // try password from env
        val envPassword = System.getenv(mcPasswordEnv) ?: ""
        if (!envPassword.isEmpty()) {
            try {
                val auth = AuthenticationService(cToken)
                auth.username = username
                auth.password = envPassword
                auth.login()
                // success!
                cacheToken(authCachePath, auth)
                return mcProtoFromAuth(auth)
            } catch (e: InvalidCredentialsException) {
                logger.warning("Invalid password provided via env ($mcPasswordEnv)")
            }
        }

        // try password from .credentials
        val credentials = readCredentialsFile(credentialsPath)
        if (credentials == null) {
            logger.fine("Could not read credentials from $credentialsPath and no password provided via env ($mcPasswordEnv)")
            return null
        } else {
            val credPassword = credentials[username] ?: ""
            if (credPassword.isEmpty()) {
                logger.info("No password provided via env ($mcPasswordEnv) or $credentialsPath")
                return null
            } else {
                try {
                    val auth = AuthenticationService(cToken)
                    auth.username = username
                    auth.password = credPassword
                    auth.login()
                    // success!
                    cacheToken(authCachePath, auth)
                    return mcProtoFromAuth(auth)
                } catch (e: InvalidCredentialsException) {
                    logger.warning("Invalid password provided via $credentialsPath")
                    return null
                }
            }
        }
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

class AuthModule : Module() {
    override fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        val auth = AuthenticationProvider(
            System.getProperty("mcAuthCredentials") ?: ".credentials",
            System.getProperty("mcAuthCache") ?: ".auth_tokens.json"
        )
        serviceRegistry.provideService(Authentication::class.java, auth)
    }
}

private fun readAuthTokenCache(authCachePath: String): AuthTokenCache? {
    val configStr: String
    try {
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

fun parseCredentialsFile(lines: List<String>): Map<String, String> {
    return lines
        .filter { line -> line.isNotEmpty() }
        .map { line ->
            val split = line.split(" ", limit = 2)
            split[0] to split[1]
        }.toMap()
}

private fun mcProtoFromAuth(auth: AuthenticationService): MinecraftProtocol {
    return MinecraftProtocol(auth.selectedProfile.name, auth.clientToken, auth.accessToken)
}
