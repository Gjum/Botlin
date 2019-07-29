package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.api.MainArgs
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import com.github.gjum.minecraft.botlin.behaviors.AutoReconnect
import com.github.gjum.minecraft.botlin.behaviors.BlindPhysics
import com.github.gjum.minecraft.botlin.behaviors.EventLogger
import com.github.gjum.minecraft.botlin.defaults.*
import com.github.gjum.minecraft.botlin.modules.ConfigFileModulesLoader
import com.github.gjum.minecraft.botlin.modules.ReloadableServiceRegistry
import com.github.gjum.minecraft.botlin.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.io.File

object Main {
	@JvmStatic
	fun main(args: Array<String>) {
		Log.reload() // TODO why is this needed? JUL should automatically read logging.properties

		fun constructDefaultModules() = listOf(
			AuthModule(),
			AutoReconnect(),
			AvatarModule(),
			BlindPhysics(),
			CliModule(),
			CommandModule(),
			EventLogger(),
			MainArgsModule(args), // special
			UsefulCommandsModule()
		)

		val configPath = System.getProperty("modulesConfig")?.let { File(it) }
		val modulesLoader = ConfigFileModulesLoader(configPath, ::constructDefaultModules)
		val serviceRegistry = ReloadableServiceRegistry(modulesLoader)
		runBlocking {
			serviceRegistry.reloadModules()
			serviceRegistry.coroutineContext[Job]!!.join()
		}
	}
}

private class MainArgsModule(private val args: Array<String>) : Module() {
	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		serviceRegistry.provideService(MainArgs::class.java, object : MainArgs {
			override val args get() = this@MainArgsModule.args
		})
	}
}
