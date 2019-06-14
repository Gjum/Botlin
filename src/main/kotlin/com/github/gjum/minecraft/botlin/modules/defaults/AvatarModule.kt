package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.Avatars
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry
import com.github.gjum.minecraft.botlin.state.AvatarImpl
import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.auth.service.ProfileService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// TODO method to forget avatar?
class AvatarProvider(private val avatars: MutableMap<String, Avatar>) : Avatars {
    private val profileService = ProfileService()

    override suspend fun getAvatar(username: String, serverAddress: String): Avatar {
        return avatars.getOrPut("$username@$serverAddress") {
            val profile: GameProfile = lookupProfile(username)
            AvatarImpl(profile, serverAddress)
        }
    }

    private suspend fun lookupProfile(username: String): GameProfile {
        return suspendCancellableCoroutine { cont ->
            profileService.findProfilesByName(arrayOf(username),
                object : ProfileService.ProfileLookupCallback {
                    override fun onProfileLookupSucceeded(profile: GameProfile?) {
                        cont.resume(profile!!)
                    }

                    override fun onProfileLookupFailed(profile: GameProfile?, e: Exception?) {
                        cont.resumeWithException(e!!)
                    }
                }
            )
        }
    }
}

// TODO hot reload should retain previous avatars' state
class AvatarModule : Module() {
    override suspend fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        serviceRegistry.provideService(Avatars::class.java,
            AvatarProvider(mutableMapOf()))
    }
}
