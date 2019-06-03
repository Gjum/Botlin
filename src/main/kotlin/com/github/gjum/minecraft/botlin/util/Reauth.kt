package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.modules.defaults.parseCredentialsFile
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

private class AuthTokenCache(val clientToken: String, val sessions: MutableMap<String, String> = mutableMapOf())

object Reauth {
    private val logger: Logger = Logger.getLogger(this::class.java.name)

    /**
     * If [accessToken] is given or can be read from [authCachePath],
     * tries to authenticate with that token, refreshing it if necessary,
     * using [UUID.randomUUID] if [clientToken] is unset.
     *
     * If no [accessToken] was given and [authCachePath] had no tokens for the [username],
     * but [password] is given, tries to authenticate with that password,
     * using [UUID.randomUUID] if [clientToken] is unset.
     *
     * Otherwise, does not authenticate, returning an "offline" account
     * (signalled with [MinecraftProtocol.accessToken] == "").
     *
     * If any [accessToken] or [password] was provided (via argument or [authCachePath]),
     * caches the access token in [authCachePath] if authenticated successfully,
     * or fails with [RequestException] if unsuccessful.
     */
    @Throws(RequestException::class)
    fun reauth(
        username: String,
        password: String = "",
        accessToken: String = "",
        clientToken: String = "",
        authCachePath: String? = ".auth_tokens.json",
        credentialsPath: String? = ".credentials"
    ): MinecraftProtocol {
        var aToken = accessToken
        var cToken = clientToken
        var bestPassword = password
        var authCache: AuthTokenCache? = null
        if (aToken == "" && authCachePath != null) {
            var configStr = ""
            try {
                configStr = String(Files.readAllBytes(Paths.get(authCachePath)))
            } catch (e: IOException) {
                logger.info("Could not read auth token cache from $authCachePath - trying password")
            }
            if (configStr != "") {
                authCache = Gson().fromJson(configStr, AuthTokenCache::class.java)
                cToken = authCache?.clientToken ?: ""
                aToken = authCache?.sessions?.get(username) ?: ""
            }
        }

        if (aToken == "" && bestPassword == "") {
            // try .credentials
            var credentialsLines = emptyList<String>()
            try {
                credentialsLines = Files.readAllLines(Paths.get(credentialsPath))
            } catch (e: IOException) {
                logger.info("Could not read credentials from $credentialsPath")
            }
            if (credentialsLines.isNotEmpty()) {
                val credentials = parseCredentialsFile(credentialsLines)
                val userOrDefault = nonemptyOrNull(username)
                    ?: runCatching { credentials.keys.first() }.getOrNull()
                    ?: ""
                bestPassword = credentials[userOrDefault] ?: ""
            }
        }

        if (aToken == "" && bestPassword == "") {
            // cannot possibly authenticate
            logger.warning("Neither password nor auth token known - running in offline mode")
            return MinecraftProtocol(username)
        }

        cToken = if (cToken != "") cToken else UUID.randomUUID().toString()
        val auth = AuthenticationService(cToken)
        auth.username = username
        auth.password = bestPassword
        auth.accessToken = aToken

        try {
            auth.login()
        } catch (e: InvalidCredentialsException) {
            if (bestPassword != "" && auth.accessToken != "") {
                logger.fine("Auth: invalid token. Retrying with password")
                auth.accessToken = ""
                auth.login()
            } else throw e
        }

        // if token changed, write to auth cache file
        if (auth.accessToken != aToken && authCachePath != null) {
            authCache = authCache ?: AuthTokenCache(cToken, mutableMapOf())
            authCache.sessions[username] = auth.accessToken
            try {
                Files.write(Paths.get(authCachePath), Gson().toJson(authCache).toByteArray())
            } catch (e: IOException) {
                logger.warning("Could not write auth token cache to $authCachePath")
            }
        }

        return MinecraftProtocol(auth.selectedProfile.name, auth.clientToken, auth.accessToken)
    }
}

private fun nonemptyOrNull(s: String) = if (s == "") null else s
