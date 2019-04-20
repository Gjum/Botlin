package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.Log.log
import com.github.steveice10.mc.auth.exception.request.RequestException
import com.github.steveice10.mc.auth.service.AuthenticationService
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.google.gson.Gson
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.logging.Level

private class AuthTokenCache(val clientToken: String, val sessions: MutableMap<String, String>)

class AuthResult(val protocol: MinecraftProtocol?, val error: Throwable?)

object Reauth {
    fun reauth(
        username: String,
        password: String = "",
        accessToken: String = "",
        clientToken: String = "",
        authCachePath: String? = ".auth_tokens.json"
    ): AuthResult {
        var aToken = accessToken
        var cToken = clientToken
        var authCache: AuthTokenCache? = null
        if (aToken == "" && authCachePath != null) {
            var configStr = ""
            try {
                configStr = String(Files.readAllBytes(Paths.get(authCachePath)))
            } catch (e: IOException) {
                log("Could not read auth token cache from $authCachePath", Level.WARNING)
            }
            if (configStr != "") {
                authCache = Gson().fromJson(configStr, AuthTokenCache::class.java)
                cToken = authCache?.clientToken ?: ""
                aToken = authCache?.sessions?.get(username) ?: ""
            }
        }

        if (aToken == "" && password == "") {
            // cannot possibly authenticate
            return AuthResult(MinecraftProtocol(username), null)
        }

        cToken = if (cToken != "") cToken else UUID.randomUUID().toString()
        val auth = AuthenticationService(cToken)
        auth.username = username
        auth.password = password
        auth.accessToken = aToken

        try {
            auth.login()
        } catch (e: RequestException) {
            return AuthResult(null, e)
        }

        // if token changed, write to auth cache file
        if (password != "" && auth.accessToken != aToken && authCachePath != null) {
            authCache = authCache ?: AuthTokenCache(cToken, mutableMapOf())
            authCache.sessions[username] = auth.accessToken
            try {
                Files.write(Paths.get(authCachePath), Gson().toJson(authCache).toByteArray())
            } catch (e: IOException) {
                log("Could not write auth token cache to $authCachePath", Level.WARNING)
            }
        }

        return AuthResult(MinecraftProtocol(auth.selectedProfile, auth.accessToken), null)
    }
}
