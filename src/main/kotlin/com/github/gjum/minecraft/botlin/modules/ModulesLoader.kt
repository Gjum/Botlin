package com.github.gjum.minecraft.botlin.modules

import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.net.URLConnection
import java.util.*
import java.util.logging.Logger

interface ModulesLoader<T> {
    fun reload(modulesDir: File? = null): Collection<T>?
    fun getAvailableModules(): Collection<T>
}

class DirectoryModulesLoader<T>(
    private val moduleClass: Class<T>,
    private val defaultModulesDir: File = File("modules")
) : ModulesLoader<T> {
    private var classLoader: URLClassLoader? = null

    @Synchronized
    override fun reload(modulesDir: File?): Collection<T>? {
        if (classLoader != null) {
            try {
                classLoader!!.close() // TODO when is a good time to close it? do we actually need to? is this a memleak?
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        var urlConnection: URLConnection? = null
        var wasUseCaches = true
        try {
            urlConnection = URL("file:/").openConnection()
            wasUseCaches = urlConnection!!.defaultUseCaches
            urlConnection.defaultUseCaches = false
        } catch (e: IOException) {
            logger.severe("Failed to disable jar cache")
            e.printStackTrace()
        }

        classLoader = makeContextClassLoaderWithModulesDir(modulesDir ?: defaultModulesDir)
        val modules = getAvailableModules()

        if (urlConnection != null) {
            urlConnection.defaultUseCaches = wasUseCaches
        }

        return modules
    }

    @Synchronized
    override fun getAvailableModules(): Collection<T> {
        if (classLoader == null) reload()
        try {
            val modules = LinkedList<T>()
            val serviceLoader = ServiceLoader.load(moduleClass, classLoader)
            val iterator = serviceLoader?.iterator() ?: throw RuntimeException(
                "reload did not set serviceLoader")
            while (iterator.hasNext()) {
                try {
                    val loadableModule = iterator.next()
                    modules.add(loadableModule)
                } catch (e: ServiceConfigurationError) {
                    logger.severe("Failed to load a module, here's some debug info for its dev:")
                    e.printStackTrace()
                }
            }
            return modules
        } catch (e: ServiceConfigurationError) {
            logger.severe("Failed to load a module, here's some debug info for its dev:")
            e.printStackTrace()
            return emptyList()
        }
    }

    private companion object {
        private val logger: Logger = Logger.getLogger(this::class.java.name)

        private fun makeContextClassLoaderWithModulesDir(dir: File): URLClassLoader {
            if (!dir.exists() && !dir.mkdir()) {
                logger.warning("Could not create modules directory $dir")
            }
            val urlsList = ArrayList<URL>()
            val files = Objects.requireNonNull(dir.listFiles(), "Invalid modules directory $dir")
            for (file in files) {
                if (file.name.endsWith(".jar")) {
                    try {
                        val url = file.toURI().toURL()
                        urlsList.add(url)
                    } catch (e: IOException) {
                        logger.severe("Failed to load jar, invalid path")
                        e.printStackTrace()
                    }
                }
            }
            val urls = urlsList.toTypedArray()

            val threadCL = Thread.currentThread().contextClassLoader
            return URLClassLoader(urls, threadCL)
        }
    }
}
