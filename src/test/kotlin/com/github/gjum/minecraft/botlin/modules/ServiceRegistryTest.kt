package com.github.gjum.minecraft.botlin.modules

import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.Service
import io.mockk.*
import kotlin.test.Test
import kotlin.test.assertEquals

typealias Handler = (String) -> Unit

interface TestService : Service {
    fun subscribe(caller: Module, event: String, handler: Handler)
    fun emit(evt: String, msg: String)
}

class ConsumerModule : Module() {
    override fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        serviceRegistry.consumeService(TestService::class.java,
            ::handleTestServiceChange)
    }

    fun handleTestServiceChange(service: TestService?) {
        service?.subscribe(this, "foo", ::fooHandler)
    }

    fun fooHandler(msg: String) {
        assertEquals("bar", msg)
    }
}

class EventProvider : TestService {
    private val handlers = mutableMapOf<String, MutableCollection<Handler>>()

    override fun subscribe(caller: Module, event: String, handler: Handler) {
        handlers.getOrPut(event) { mutableListOf() }.add(handler)
    }

    override fun emit(evt: String, msg: String) {
        handlers["foo"]?.forEach { it(msg) }
    }
}

class ProviderModule(private val provider: EventProvider) : Module() {
    override fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        serviceRegistry.provideService(TestService::class.java, provider)
    }

    fun externalFooEvent(msg: String) {
        provider.emit("foo", msg)
    }
}

class ServiceRegistryTest {
    @Test
    fun `sets classpath as default module name`() {
        val consumerModule = ConsumerModule()
        assertEquals("com.github.gjum.minecraft.botlin.modules.ConsumerModule", consumerModule.name)
    }

    @Test
    fun `loads modules and connects services`() {
        val consumerModule = spyk(ConsumerModule())
        val provider = spyk(EventProvider())
        val providerModule = spyk(ProviderModule(provider))
        val modules = listOf(consumerModule, providerModule)

        val loader = mockk<ModulesLoader<Module>>()
        every { loader.reload() } returns modules
        every { loader.getAvailableModules() } returns modules

        // execute test
        val serviceRegistry = ServiceRegistry(loader)
        providerModule.externalFooEvent("bar")

        verifyOrder {
            loader.getAvailableModules()
            consumerModule.name
            providerModule.name

            consumerModule.name
            consumerModule.initialize(serviceRegistry)

            providerModule.name
            providerModule.initialize(serviceRegistry)
            consumerModule.handleTestServiceChange(provider)
            provider.subscribe(consumerModule, "foo", any())

            providerModule.externalFooEvent("bar")
            provider.emit("foo", "bar")
            consumerModule.fooHandler("bar")
        }
        verifyAll(inverse = true) {
            consumerModule.handleTestServiceChange(null)
        }

        confirmVerified(loader)
        confirmVerified(consumerModule)
        confirmVerified(provider)
        confirmVerified(providerModule)
    }

    @Test
    fun `ignores modules with same name as other already loaded module`() {
        val consumer1 = spyk(ConsumerModule())
        val consumer2 = spyk(ConsumerModule())
        val modules = listOf(consumer1, consumer2)
        val loader = mockk<ModulesLoader<Module>>()
        every { loader.reload() } returns modules
        every { loader.getAvailableModules() } returns modules

        val serviceRegistry = ServiceRegistry(loader)

        verify(inverse = true) {
            consumer1.initialize(serviceRegistry)
        }
        verifyOrder {
            consumer1.name
            consumer2.name
            consumer2.initialize(serviceRegistry)
        }
    }
}
