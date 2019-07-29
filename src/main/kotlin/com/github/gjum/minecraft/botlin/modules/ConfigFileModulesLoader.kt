package com.github.gjum.minecraft.botlin.modules

import com.github.gjum.minecraft.botlin.api.Module
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.net.URLClassLoader
import java.util.logging.Logger

private const val defaultLoadDefaultModules = true
private const val defaultBaseDir = "modules"

// XXX unused; also clean up commented code below
private data class Config(
	val modules: Map<String, Collection<String>?>? = null,
	val loadDefaultModules: Boolean? = defaultLoadDefaultModules,
	val baseDir: String? = defaultBaseDir
)

class ConfigFileModulesLoader(
	private val configPath: File?,
	private val forcedModules: Collection<Module>,
	private val constructDefaultModules: () -> Collection<Module>
) : ModulesLoader<Module> {
	private val logger = Logger.getLogger(this::class.java.name)

	private var classLoader: ClassLoader? = null
	private var modules: Collection<Module> = emptyList()

	@Synchronized
	override fun getAvailableModules(): Collection<Module> {
		if (modules.isEmpty()) reload()
		return modules
	}

	@Synchronized
	override fun reload(): Collection<Module> {
		val config = configPath?.reader()?.use { Yaml().load(it) } as Map<String, Any?>?// as Config?
		val modulesConfig = config?.get("modules") as Map<String, Collection<String>>?//?.modules

		if (modulesConfig == null || config == null || configPath == null) {
			modules = constructDefaultModules() + forcedModules
			return getAvailableModules()
		}

//		val baseDir = config.baseDir ?: defaultBaseDir
		val baseDir = (config["baseDir"] as String?) ?: defaultBaseDir
		val baseDirFile = File(configPath.parent, baseDir)
		val jarUrls = modulesConfig.keys.map { jarPathStr ->
			val jarFile = if (File(jarPathStr).isAbsolute) {
				File(jarPathStr)
			} else {
				File(baseDirFile, jarPathStr)
			}
			jarFile.toURI().toURL()
		}.toTypedArray()

		val threadCL = Thread.currentThread().contextClassLoader
		classLoader = URLClassLoader(jarUrls, threadCL)

		// TODO use separate ClassLoader for each key
		val newModules = mutableListOf<Module>()
		modulesConfig.values.flatten().map { classPath ->
			val cls = classLoader!!.loadClass(classPath)
			try {
				val module = cls.getConstructor().newInstance() as Module
				newModules.add(module)
			} catch (e: Throwable) { // and rethrow
				logger.severe("Failed to load a module, here's some debug info for its dev:")
				e.printStackTrace()
				throw e
			}
		}

//		val loadDefaultModules = config.loadDefaultModules ?: defaultLoadDefaultModules
		val loadDefaultModules = (config["loadDefaultModules"] as Boolean?) ?: defaultLoadDefaultModules
		if (loadDefaultModules) {
			// duplicates are ok because they need to get deduplicated by name anyway
			newModules.addAll(constructDefaultModules())
		}

		// duplicates are ok because they need to get deduplicated by name anyway
		newModules.addAll(forcedModules)

		// no errors during initializing modules
		modules = newModules
		return getAvailableModules()
	}
}
