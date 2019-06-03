package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry

/**
 * Wraps stdin/stdout/stderr to set up a command line interface.
 */
class CliModule : Module() {
    override fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        // XXX set up commands
        // XXX set up cli
    }

    override fun teardown(newModule: Module?) {
        // XXX tear down cli
    }
}
