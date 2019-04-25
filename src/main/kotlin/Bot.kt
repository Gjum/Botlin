package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.Log.logger
import com.github.gjum.minecraft.botlin.Look.Companion.radFromDeg
import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.data.game.PlayerListEntryAction
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility
import com.github.steveice10.mc.protocol.data.game.world.notify.ClientNotification
import com.github.steveice10.mc.protocol.data.game.world.notify.ThunderStrengthValue
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientPluginMessagePacket
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientSettingsPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.*
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.*
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.*
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.*
import com.github.steveice10.mc.protocol.packet.ingame.server.window.*
import com.github.steveice10.mc.protocol.packet.ingame.server.world.*
import com.github.steveice10.mc.protocol.packet.login.server.EncryptionRequestPacket
import com.github.steveice10.mc.protocol.packet.login.server.LoginDisconnectPacket
import com.github.steveice10.mc.protocol.packet.login.server.LoginSetCompressionPacket
import com.github.steveice10.mc.protocol.packet.login.server.LoginSuccessPacket
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.event.session.*
import com.github.steveice10.packetlib.packet.Packet
import kotlinx.coroutines.*
import java.net.SocketAddress
import java.util.*
import java.util.logging.Level
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass

/**
 * An enhanced client that tracks world state,
 * handles disconnection, and can be reconnected.
 * Implemented by [McBot].
 * See also [BotConnected], [BotAlive], [InventoryTransaction].
 */
interface IBot : CoroutineScope {
    var profile: GameProfile?
    val endReason: String?
    val connected: Boolean
    val localAddress: SocketAddress?
    val remoteAddress: SocketAddress?
    fun disconnect(reason: String?, cause: Throwable? = null)
    fun send(packet: Packet)
    fun registerListeners(handler: Any)
    fun unregisterListeners(handler: Any)

    val entity: Entity?
    val health: Float?
    val food: Int?
    val saturation: Float?
    val experience: Experience?
    val position: Vec3d? get() = entity?.position
    val look: Look? get() = entity?.look
    val onGround: Boolean? get() = entity?.onGround
    val gameMode: GameMode?
    val eyePos: Vec3d? get() = entity?.eyePos
    val inventory: McWindow?

    val spawned: Boolean
        get() = (position != null
                && entity != null
                && health != null
                && experience != null
                && connected)

    val alive get() = health ?: 0.0f > 0.0f && spawned

    var world: World?
    val playerList: Map<UUID, PlayerListItem>
}

private val brandBytesVanilla = byteArrayOf(7, 118, 97, 110, 105, 108, 108, 97)

/**
 * Implementation of [IBot].
 */
class McBot : IBot, SessionListener {
    private var connection: Session? = null
    private var ticker: Job? = null

    override val coroutineContext = EmptyCoroutineContext

    override var profile: GameProfile? = null
    override var endReason: String? = null
    override val connected get() = connection != null && endReason == null && profile != null

    override var entity: Entity? = null
    override var health: Float? = null
    override var food: Int? = null
    override var saturation: Float? = null
    override var experience: Experience? = null
    override var inventory: McWindow? = null

    override var world: World? = null
    override val playerList = mutableMapOf<UUID, PlayerListItem>()

    override var gameMode: GameMode?
        get() = entity?.playerListItem?.gameMode
        set(v) {
            if (entity?.playerListItem == null) {
                entity?.playerListItem = PlayerListItem(profile ?: error("No UUID in client"))
            }
            entity?.playerListItem?.gameMode = v
        }

    override val localAddress get() = connection?.localAddress
    override val remoteAddress get() = connection?.remoteAddress

    override fun send(packet: Packet) = connection?.send(packet) ?: Unit

    private fun reset() {
        endReason = null

        ticker?.cancel()
        ticker = null

        entity = null
        health = null
        food = null
        saturation = null
        inventory = null

        world = null
        playerList.clear()
    }

    private val listeners = mutableMapOf<KClass<out Any>, MutableCollection<Any>>()

    private fun <T : Any> emitEvent(listener: KClass<T>, block: T.() -> Unit) {
        listeners[listener]?.forEach { handlerAny ->
            try {
                @Suppress("UNCHECKED_CAST")
                val handler = handlerAny as T
                block.invoke(handler)
            } catch (e: Throwable) {
                logger.log(Level.SEVERE, "Unhandled error in ${listener.simpleName} handler", e)
            }
        }
    }

    override fun registerListeners(handler: Any) {
        for (listener in listenerInterfaces) {
            if (listener.isInstance(handler)) {
                val handlers = listeners.getOrPut(listener) { mutableListOf() }
                handlers.add(handler)
            }
        }
    }

    override fun unregisterListeners(handler: Any) {
        for (listener in listenerInterfaces) {
            if (listener.isInstance(handler)) {
                val handlers = listeners.getOrPut(listener) { mutableListOf() }
                handlers.remove(handler)
                if (handlers.isEmpty()) {
                    listeners.remove(listener)
                }
            }
        }
    }

    private fun getEntityOrCreate(eid: Int): Entity {
        return world!!.entities.getOrPut(eid) { Entity(eid) }
    }

    /**
     * Throws [NotImplementedError] if already connected.
     */
    fun useConnection(connection: Session, profile: GameProfile): McBot {
        if (this.connection != null) TODO("already connected") // bail? close existing?
        reset()
        this.connection = connection
        this.profile = profile
        connection.addListener(this)
        return this
    }

    override fun connected(event: ConnectedEvent) {
        emitEvent(IReadyListener::class) { onConnected(event.session) }
    }

    override fun disconnecting(event: DisconnectingEvent) {
        event.apply { disconnect(reason, cause) }
    }

    override fun disconnected(event: DisconnectedEvent) {
        event.apply { disconnect(reason, cause) }
    }

    /**
     * Disconnects the client, blocking the current thread.
     */
    override fun disconnect(reason: String?, cause: Throwable?) {
        if (connection == null) return
        if (endReason == null) {
            emitEvent(IReadyListener::class) { onDisconnected(reason, cause) }
        }
        // reset() // TODO do we already reset here or remember the failstate?
        endReason = reason
        // TODO submit upstream patch for TcpClientSession overriding all TcpSession#disconnect
        connection?.disconnect(reason, cause, true)
        connection = null
    }

    override fun packetReceived(event: PacketReceivedEvent) {
        val packet = event.getPacket<Packet>()
        try {
            processPacket(packet)
        } catch (e: Throwable) {
            logger.log(Level.SEVERE, "Failed to process received packet $packet", e)
        }
        emitEvent(IPacketListener::class) { onServerPacketReceived(packet) }
    }

    private fun processPacket(packet: Packet) {
        when (packet) {
            is ServerChatPacket -> {
                emitEvent(IChatListener::class) { onChatReceived(packet.message) }
            }
            is ServerJoinGamePacket -> {
                world = World(packet.dimension)
                entity = getEntityOrCreate(packet.entityId).apply { uuid = profile?.id }
                gameMode = packet.gameMode
                send(ClientPluginMessagePacket("MC|Brand", brandBytesVanilla))
                send(ClientSettingsPacket("en_us", 10, ChatVisibility.FULL, true, emptyArray(), Hand.MAIN_HAND))
            }
            is ServerRespawnPacket -> {
                world = World(packet.dimension)
                gameMode = packet.gameMode
                health = null
                food = null
                saturation = null
                inventory = null
            }
            is ServerPlayerHealthPacket -> {
                val wasSpawned = spawned
                health = packet.health
                food = packet.food
                saturation = packet.saturation
                if (!wasSpawned && spawned) emitEvent(IReadyListener::class) { onSpawned() }
            }
            is ServerPlayerSetExperiencePacket -> {
                val wasSpawned = spawned
                experience = Experience(
                    packet.slot,
                    packet.level,
                    packet.totalExperience
                )
                if (!wasSpawned && spawned) emitEvent(IReadyListener::class) { onSpawned() }
            }
            is ServerPlayerPositionRotationPacket -> {
                send(ClientTeleportConfirmPacket(packet.teleportId))
                val wasSpawned = spawned

                if (packet.relativeElements.isEmpty()) {
                    entity?.position = Vec3d(packet.x, packet.y, packet.z)
                    entity?.look = Look(packet.yaw.toRadians(), packet.pitch.toRadians())
                } else {
                    // TODO parse flags field: absolute vs relative coords
                    // for now, crash cleanly, instead of continuing with wrong pos
                    connection?.disconnect("physics.position_packet_flags_not_implemented ${packet.relativeElements}")
                    return
                }

                emitEvent(IPlayerStateListener::class) { onPositionChanged(position!!) }
                if (!wasSpawned && spawned) emitEvent(IReadyListener::class) { onSpawned() }

                startTicker()
            }
            is ServerVehicleMovePacket -> {
                val vehicle = entity?.vehicle ?: error("Server moved bot while not in vehicle")
                vehicle.apply {
                    position = Vec3d(packet.x, packet.y, packet.z)
                    look = Look.fromDegrees(packet.yaw, packet.pitch)
                    entity?.position = position // TODO update client pos in relation to vehicle (typically up/down)
                    emitEvent(IPlayerStateListener::class) { onPositionChanged(position!!) }
                }
            }
            is ServerPlayerListEntryPacket -> {
                for (item in packet.entries) {
                    val wasInListBefore = item.profile.id in playerList
                    val player = playerList.getOrPut(item.profile.id) { PlayerListItem(item.profile) }
                    if (packet.action === PlayerListEntryAction.ADD_PLAYER) {
                        player.apply {
                            gameMode = item.gameMode
                            ping = item.ping
                            displayName = item.displayName
                        }
                        if (!wasInListBefore) {
                            emitEvent(IPlayerListListener::class) {
                                onPlayerJoined(player)
                            }
                        }
                    } else if (packet.action === PlayerListEntryAction.UPDATE_GAMEMODE) {
                        player.gameMode = item.gameMode
                    } else if (packet.action === PlayerListEntryAction.UPDATE_LATENCY) {
                        player.ping = item.ping
                    } else if (packet.action === PlayerListEntryAction.UPDATE_DISPLAY_NAME) {
                        player.displayName = item.displayName
                    } else if (packet.action === PlayerListEntryAction.REMOVE_PLAYER) {
                        playerList.remove(item.profile.id)
                        if (wasInListBefore) {
                            emitEvent(IPlayerListListener::class) {
                                onPlayerLeft(player)
                            }
                        }
                    }
                }
            }

            is ServerSpawnPlayerPacket -> {
                val entity = getEntityOrCreate(packet.entityId).apply {
                    type = EntityType.Player
                    metadata = packet.metadata
                    uuid = packet.uuid
                    position = Vec3d(packet.x, packet.y, packet.z)
                    look = Look.fromDegrees(packet.yaw, packet.pitch)
                }
                val player = playerList[packet.uuid]
                if (player == null) {
                    logger.warning("SpawnPlayer: unknown uuid ${packet.uuid} for eid ${packet.entityId}")
                } else {
                    entity.playerListItem = player
                    player.entity = entity
                }
            }
            is ServerSpawnObjectPacket -> {
                getEntityOrCreate(packet.entityId).apply {
                    uuid = packet.uuid
                    type = EntityType.Object(packet.type, packet.data)
                    position = Vec3d(packet.x, packet.y, packet.z)
                    look = Look.fromDegrees(packet.yaw, packet.pitch)
                    velocity = Vec3d(packet.motionX, packet.motionY, packet.motionZ)
                }
            }
            is ServerSpawnMobPacket -> {
                getEntityOrCreate(packet.entityId).apply {
                    uuid = packet.uuid
                    type = EntityType.Mob(packet.type)
                    metadata = packet.metadata
                    position = Vec3d(packet.x, packet.y, packet.z)
                    look = Look.fromDegrees(packet.yaw, packet.pitch)
                    headYaw = radFromDeg(packet.headYaw.toDegrees())
                    velocity = Vec3d(packet.motionX, packet.motionY, packet.motionZ)
                }
            }
            is ServerSpawnPaintingPacket -> {
                getEntityOrCreate(packet.entityId).apply {
                    type = EntityType.Painting(packet.paintingType, packet.direction)
                    uuid = packet.uuid
                    position = packet.position.run { Vec3i(x, y, z).asVec3d() }
                }
            }
            is ServerSpawnExpOrbPacket -> {
                getEntityOrCreate(packet.entityId).apply {
                    type = EntityType.ExpOrb(packet.exp)
                    position = Vec3d(packet.x, packet.y, packet.z)
                }
            }
            is ServerSpawnGlobalEntityPacket -> {
                getEntityOrCreate(packet.entityId).apply {
                    type = EntityType.Global(packet.type)
                    position = Vec3d(packet.x, packet.y, packet.z)
                }
            }
            is ServerEntityDestroyPacket -> {
                world?.entities?.also { entities ->
                    for (eid in packet.entityIds) {
                        entities.remove(eid)?.apply { playerList[uuid]?.entity = null }
                    }
                }
            }
            is ServerEntityTeleportPacket -> {
                getEntityOrCreate(packet.entityId).apply {
                    position = Vec3d(packet.x, packet.y, packet.z)
                    look = Look.fromDegrees(packet.yaw, packet.pitch)
                    onGround = packet.isOnGround
                }
            }
            is ServerEntityVelocityPacket -> {
                getEntityOrCreate(packet.entityId).velocity = Vec3d(
                    packet.motionX, packet.motionY, packet.motionZ
                )
            }
            is ServerEntityPositionPacket -> {
                getEntityOrCreate(packet.entityId).position?.also {
                    it += Vec3d(
                        packet.movementX / (128 * 32),
                        packet.movementY / (128 * 32),
                        packet.movementZ / (128 * 32)
                    )
                }
            }
            is ServerEntityPositionRotationPacket -> {
                getEntityOrCreate(packet.entityId).position?.also {
                    it += Vec3d(
                        packet.movementX,
                        packet.movementY,
                        packet.movementZ
                    )
                }
                getEntityOrCreate(packet.entityId).look =
                    Look.fromDegrees(packet.yaw, packet.pitch)
            }
            is ServerEntityRotationPacket -> {
                getEntityOrCreate(packet.entityId).look = Look.fromDegrees(packet.yaw, packet.pitch)
            }
            is ServerEntityHeadLookPacket -> {
                getEntityOrCreate(packet.entityId).headYaw = Look.radFromDeg(packet.headYaw.toDegrees())
            }
            is ServerEntityAttachPacket -> {
                getEntityOrCreate(packet.entityId).vehicle = getEntityOrCreate(packet.attachedToId)
                // TODO attach vice versa?
            }
            is ServerEntitySetPassengersPacket -> {
                val passengers = (packet.passengerIds).map(this::getEntityOrCreate).toTypedArray()
                val entity = getEntityOrCreate(packet.entityId)
                entity.passengers = passengers
                passengers.forEach { it.vehicle = entity }
            }
            is ServerEntityMetadataPacket -> {
                val entity = getEntityOrCreate(packet.entityId)
                entity.updateMetadata(packet.metadata)
                if (entity === this.entity) {
                    emitEvent(IPlayerStateListener::class) {
                        onPlayerEntityStatusChanged()
                    }
                }
            }
            is ServerEntityPropertiesPacket -> TodoEntityPacket // TODO emit PlayerEntityStatusChanged
            is ServerEntityEquipmentPacket -> TodoEntityPacket
            is ServerEntityEffectPacket -> TodoEntityPacket // TODO emit PlayerEntityStatusChanged
            is ServerEntityRemoveEffectPacket -> TodoEntityPacket // TODO emit PlayerEntityStatusChanged
            is ServerEntityStatusPacket -> TodoEntityPacket // TODO emit PlayerEntityStatusChanged
            is ServerEntityAnimationPacket -> TodoEntityPacket
            is ServerEntityCollectItemPacket -> TodoEntityPacket

            is ServerChunkDataPacket -> {
                val column = packet.column
                world?.updateColumn(column.x, column.z, column)
            }
            is ServerUnloadChunkPacket -> {
                world?.unloadColumn(packet.x, packet.z)
            }
            is ServerBlockChangePacket -> {
                val change = packet.record
                world?.setBlock(change.position, change.block)
            }
            is ServerMultiBlockChangePacket -> {
                for (change in packet.records) {
                    world?.setBlock(change.position, change.block)
                }
            }
            is ServerBlockValuePacket -> {
                packet.apply {
                    world?.setBlockData(position, blockId, type, value)
                }
            }

            is ServerPlayerChangeHeldItemPacket -> TodoInventoryPacket
            is ServerOpenWindowPacket -> TodoInventoryPacket
            is ServerCloseWindowPacket -> TodoInventoryPacket
            is ServerWindowItemsPacket -> TodoInventoryPacket
            is ServerWindowPropertyPacket -> TodoInventoryPacket
            is ServerSetSlotPacket -> TodoInventoryPacket
            is ServerConfirmTransactionPacket -> TodoInventoryPacket

            is ServerNotifyClientPacket -> {
                when (packet.notification) {
                    ClientNotification.START_RAIN -> world?.rainy = false
                    ClientNotification.STOP_RAIN -> world?.rainy = true
                    else -> Unit
                }
                val value = packet.value
                when (value) {
                    is GameMode -> gameMode = value
                    is ThunderStrengthValue -> world?.skyDarkness = value.strength.toDouble()
                    // TODO track other world states
                }
            }

            is EncryptionRequestPacket -> HandledByProtoLib
            is LoginSuccessPacket -> HandledByProtoLib
            is LoginSetCompressionPacket -> HandledByProtoLib
            is LoginDisconnectPacket -> HandledByProtoLib
            is ServerKeepAlivePacket -> HandledByProtoLib
            is ServerDisconnectPacket -> HandledByProtoLib
        }
    }

    private fun startTicker() {
        if (ticker != null) return
        ticker = launch {
            while (isActive) {
                emitEvent(IClientTickListener::class) { onPreClientTick() }

                if (position != null && look != null) {
                    send(
                        ClientPlayerPositionRotationPacket(
                            (onGround ?: true), // XXX set through physics
                            position!!.x,
                            position!!.y,
                            position!!.z,
                            look!!.yawDegrees().toFloat(),
                            look!!.pitchDegrees().toFloat()
                        )
                    )
                }

                // TODO check chat buffer

                emitEvent(IClientTickListener::class) { onPostClientTick() }
                delay(50) // XXX too slow, could instead sleep until next tick ms
            }
        }
    }

    override fun packetSending(event: PacketSendingEvent) = Unit
    override fun packetSent(event: PacketSentEvent) = Unit
}

private val HandledByProtoLib = Unit
private val TodoInventoryPacket = Unit // TODO handle inventory packets
private val TodoEntityPacket = Unit // TODO handle entity packets
