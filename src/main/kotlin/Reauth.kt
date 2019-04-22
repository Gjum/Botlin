package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.Log.logger
import com.github.steveice10.mc.auth.exception.request.RequestException
import com.github.steveice10.mc.auth.service.AuthenticationService
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.google.gson.Gson
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

private class AuthTokenCache(val clientToken: String, val sessions: MutableMap<String, String>)

object Reauth {
    /**
     * If [accessToken] is given, tries to login with that token,
     * refreshing it if necessary, using [UUID.randomUUID] if [clientToken] is unset,
     * and failing with [RequestException] if unsuccessful.
     *
     * Otherwise, tries to load the [username]'s auth token and client token
     * from [authCachePath], caches the successful result there if possible,
     * or failing with [RequestException] if the authentication was unsuccessful.
     *
     * If no [accessToken] was given and [authCachePath] had no tokens for the [username],
     * but [password] is given, tries to login with that password,
     * using [UUID.randomUUID] if [clientToken] is unset,
     * failing with [RequestException] if unsuccessful.
     *
     * Otherwise, does not authenticate, returning an "offline" account
     * (signalled with [MinecraftProtocol.accessToken] == "").
     *
     * Note that if the access token from [authCachePath] is invalid,
     * this method DOES NOT retry using the [password] even if given.
     * This may change in the future. For now, manually remove [authCachePath].
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

        auth.login() // may throw RequestException

        // if token changed, write to auth cache file
        if (password != "" && auth.accessToken != aToken && authCachePath != null) {
            authCache = authCache ?: AuthTokenCache(cToken, mutableMapOf())
            authCache.sessions[username] = auth.accessToken
            try {
                Files.write(Paths.get(authCachePath), Gson().toJson(authCache).toByteArray())
            } catch (e: IOException) {
                logger.warning("Could not write auth token cache to $authCachePath")
            }
        }

        return MinecraftProtocol(auth.selectedProfile, auth.accessToken)
    }
}
