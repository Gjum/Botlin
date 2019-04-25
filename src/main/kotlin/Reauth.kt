package com.github.gjum.minecraft.botlin

import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException
import com.github.steveice10.mc.auth.exception.request.RequestException
import com.github.steveice10.mc.auth.service.AuthenticationService
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.google.gson.Gson
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.logging.Logger

private class AuthTokenCache(val clientToken: String, val sessions: MutableMap<String, String>)

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
        authCachePath: String? = ".auth_tokens.json"
    ): MinecraftProtocol {
        var aToken = accessToken
        var cToken = clientToken
        var authCache: AuthTokenCache? = null
        if (aToken == "" && authCachePath != null) {
            var configStr = ""
            try {
                configStr = String(Files.readAllBytes(Paths.get(authCachePath)))
            } catch (e: IOException) {
                logger.info("Could not read auth token cache from $authCachePath")
            }
            if (configStr != "") {
                authCache = Gson().fromJson(configStr, AuthTokenCache::class.java)
                cToken = authCache?.clientToken ?: ""
                aToken = authCache?.sessions?.get(username) ?: ""
            }
        }

        if (aToken == "" && password == "") {
            // cannot possibly authenticate
            return MinecraftProtocol(username)
        }

        cToken = if (cToken != "") cToken else UUID.randomUUID().toString()
        val auth = AuthenticationService(cToken)
        auth.username = username
        auth.password = password
        auth.accessToken = aToken

        try {
            auth.login()
        } catch (e: InvalidCredentialsException) {
            if (password != "" && auth.accessToken != "") {
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
