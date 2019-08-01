package com.github.gjum.minecraft.botlin.modules

import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.Service
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

typealias Handler = (String) -> Unit

interface TestService : Service {
    fun subscribe(caller: Module, event: String, handler: Handler)
    fun emit(evt: String, msg: String)
}

class ConsumerModule : Module() {
    override suspend fun activate(serviceRegistry: ServiceRegistry) {
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
    override suspend fun activate(serviceRegistry: ServiceRegistry) {
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
        val serviceRegistry = runBlocking {
            ReloadableServiceRegistry(loader, coroutineContext)
                .apply { reloadModules() }
        }
        providerModule.externalFooEvent("bar")

        coVerifyOrder {
            loader.reload()

            consumerModule.activate(serviceRegistry)

            providerModule.activate(serviceRegistry)
            consumerModule.handleTestServiceChange(provider)
            provider.subscribe(consumerModule, "foo", any())

            providerModule.externalFooEvent("bar")
            provider.emit("foo", "bar")
            consumerModule.fooHandler("bar")
        }
        verifyAll(inverse = true) {
            consumerModule.handleTestServiceChange(null)
        }

        confirmVerified(loader, provider)
//        confirmVerified(consumerModule, providerModule) // TODO properly test behavior instead of implementation
    }

    @Test
    fun `ignores modules with same name as other already loaded module`() {
        val consumer1 = spyk(ConsumerModule())
        val consumer2 = spyk(ConsumerModule())
        val modules = listOf(consumer1, consumer2)
        val loader = mockk<ModulesLoader<Module>>()
        every { loader.reload() } returns modules
        every { loader.getAvailableModules() } returns modules

        val serviceRegistry = runBlocking {
            ReloadableServiceRegistry(loader, coroutineContext)
                .apply { reloadModules() }
        }

        coVerify(inverse = true) {
            consumer1.activate(serviceRegistry)
        }
        coVerifyOrder {
            consumer1.name
            consumer2.name
            consumer2.activate(serviceRegistry)
        }
    }
}
