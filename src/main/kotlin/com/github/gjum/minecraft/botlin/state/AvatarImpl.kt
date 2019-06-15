package com.github.gjum.minecraft.botlin.state

import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.api.Behavior
import com.github.gjum.minecraft.botlin.api.BehaviorInstance
import com.github.gjum.minecraft.botlin.util.*
import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.github.steveice10.mc.protocol.data.game.PlayerListEntryAction
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility
import com.github.steveice10.mc.protocol.data.game.world.notify.ClientNotification
import com.github.steveice10.mc.protocol.data.game.world.notify.ThunderStrengthValue
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientPluginMessagePacket
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientSettingsPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerMovementPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.*
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.*
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerChangeHeldItemPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerHealthPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerSetExperiencePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.*
import com.github.steveice10.mc.protocol.packet.ingame.server.window.*
import com.github.steveice10.mc.protocol.packet.ingame.server.world.*
import com.github.steveice10.mc.protocol.packet.login.server.EncryptionRequestPacket
import com.github.steveice10.mc.protocol.packet.login.server.LoginDisconnectPacket
import com.github.steveice10.mc.protocol.packet.login.server.LoginSetCompressionPacket
import com.github.steveice10.mc.protocol.packet.login.server.LoginSuccessPacket
import com.github.steveice10.packetlib.Client
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.event.session.*
import com.github.steveice10.packetlib.packet.Packet
import com.github.steveice10.packetlib.tcp.TcpSessionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.fixedRateTimer
import kotlin.coroutines.EmptyCoroutineContext

private val brandBytesVanilla = byteArrayOf(7, 118, 97, 110, 105, 108, 108, 97)

fun normalizeServerAddress(serverAddress: String): String {
    return (serverAddress.split(':') + "25565")
        .take(2).joinToString(":")
}

class AvatarImpl(override val profile: GameProfile, serverArg: String) : Avatar, SessionListener, CoroutineScope, EventEmitterImpl<AvatarEvents>() {
    private val logger: Logger = Logger.getLogger(this::class.java.name)
    private var ticker: Job? = null

    override val coroutineContext = EmptyCoroutineContext

    override val serverAddress = normalizeServerAddress(serverArg)

    override var behavior: BehaviorInstance = IdleBehaviorInstance(this)
    override var connection: Session? = null
    override var endReason: String? = null

    override var entity: Entity? = null
    override var health: Float? = null
    override var food: Int? = null
    override var saturation: Float? = null
    override var experience: Experience? = null
    override var inventory: Window? = null

    override var world: World? = null
    override var playerList: MutableMap<UUID, PlayerListItem>? = mutableMapOf()

    override var gameMode: GameMode?
        get() = entity?.playerListItem?.gameMode
        set(v) {
            if (entity?.playerListItem == null) {
                entity?.playerListItem = PlayerListItem(profile)
            }
            entity?.playerListItem?.gameMode = v
        }

    fun send(packet: Packet) = connection?.send(packet)

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
        playerList = null
    }

    private fun getEntityOrCreate(eid: Int): Entity {
        return world!!.entities.getOrPut(eid) { Entity(eid) }
    }

    override fun useBehavior(behavior: Behavior) {
        this.behavior.deactivate()
        this.behavior = behavior.activate(this)
    }

    @Synchronized
    override fun useProtocol(proto: MinecraftProtocol) {
        val split = serverAddress.split(':')
        val host = split[0]
        val port = split[1].toInt()
        val client = Client(host, port, proto, TcpSessionFactory())
        useConnection(client.session)
        client.session.connect(true)
    }

    private fun useConnection(connection: Session) {
        if (this.connection != null) TODO("already connected") // bail? close existing and use new one?
        if (connection.remoteAddress.toString() != serverAddress) {
            throw IllegalArgumentException(
                "Avatar for $serverAddress cannot connect to ${connection.remoteAddress}")
        }
        reset()
        this.connection = connection
        connection.addListener(this)
    }

    override fun connected(event: ConnectedEvent) {
        emit(AvatarEvents.Connected) { it.invoke(event.session) }
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
        val c = connection ?: return
        if (endReason == null) {
            emit(AvatarEvents.Disconnected) { it.invoke(c, reason, cause) }
        }
        // reset() // TODO do we already reset here or remember the failstate?
        endReason = reason
        // TODO submit upstream patch for TcpClientSession overriding all TcpSession#disconnect
        connection?.disconnect(reason, cause, false)
        connection = null
    }

    override fun packetReceived(event: PacketReceivedEvent) {
        val packet = event.getPacket<Packet>()
        try {
            processPacket(packet)
        } catch (e: Throwable) {
            logger.log(Level.SEVERE, "Failed to process received packet $packet", e)
        }
        emit(AvatarEvents.ServerPacketReceived) { it.invoke(packet) }
    }

    private fun processPacket(packet: Packet) {
        when (packet) {
            is ServerChatPacket -> {
                emit(AvatarEvents.ChatReceived) { it.invoke(packet.message) }
            }
            is ServerJoinGamePacket -> {
                world = World(packet.dimension)
                entity = getEntityOrCreate(packet.entityId).apply { uuid = profile.id }
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
                if (!wasSpawned && spawned) emit(AvatarEvents.Spawned) { it.invoke(entity!!) }
            }
            is ServerPlayerSetExperiencePacket -> {
                val wasSpawned = spawned
                experience = Experience(
                    packet.slot,
                    packet.level,
                    packet.totalExperience
                )
                if (!wasSpawned && spawned) emit(AvatarEvents.Spawned) { it.invoke(entity!!) }
            }
            is ServerPlayerPositionRotationPacket -> {
                send(ClientTeleportConfirmPacket(packet.teleportId))
                val wasSpawned = spawned
                val oldPosition = position

                if (packet.relativeElements.isEmpty()) {
                    entity?.position = Vec3d(packet.x, packet.y, packet.z)
                    entity?.look = Look(packet.yaw.toRadians(), packet.pitch.toRadians())
                } else {
                    // TODO parse flags field: absolute vs relative coords
                    // for now, crash cleanly, instead of continuing with wrong pos
                    connection?.disconnect("TODO: Unknown position flags: ${packet.relativeElements}")
                    return
                }

                send(
                    ClientPlayerPositionRotationPacket(
                        (onGround ?: true),
                        position!!.x,
                        position!!.y,
                        position!!.z,
                        look!!.yawDegrees().toFloat(),
                        look!!.pitchDegrees().toFloat()
                    )
                )

                emit(AvatarEvents.PositionChanged) {
                    it.invoke(position!!, oldPosition, packet)
                }
                if (!wasSpawned && spawned) emit(AvatarEvents.Spawned) { it.invoke(entity!!) }

                startTicker()
            }
            is ServerVehicleMovePacket -> {
                val vehicle = entity?.vehicle ?: error("Server moved bot while not in vehicle")
                vehicle.apply {
                    val oldPosition = position
                    position = Vec3d(packet.x, packet.y, packet.z)
                    look = Look.fromDegrees(packet.yaw, packet.pitch)
                    entity?.position = position // TODO update client pos in relation to vehicle (typically up/down)
                    emit(AvatarEvents.PositionChanged) {
                        it.invoke(position!!, oldPosition, packet)
                    }
                }
            }
            is ServerPlayerListEntryPacket -> {
                for (item in packet.entries) {
                    val wasInListBefore = item.profile.id in playerList!!
                    val player = playerList!!.getOrPut(item.profile.id) { PlayerListItem(item.profile) }
                    if (packet.action === PlayerListEntryAction.ADD_PLAYER) {
                        player.apply {
                            gameMode = item.gameMode
                            ping = item.ping
                            displayName = item.displayName
                        }
                        if (!wasInListBefore) {
                            emit(AvatarEvents.PlayerJoined) { it.invoke(player) }
                        }
                    } else if (packet.action === PlayerListEntryAction.UPDATE_GAMEMODE) {
                        player.gameMode = item.gameMode
                    } else if (packet.action === PlayerListEntryAction.UPDATE_LATENCY) {
                        player.ping = item.ping
                    } else if (packet.action === PlayerListEntryAction.UPDATE_DISPLAY_NAME) {
                        player.displayName = item.displayName
                    } else if (packet.action === PlayerListEntryAction.REMOVE_PLAYER) {
                        playerList!!.remove(item.profile.id)
                        if (wasInListBefore) {
                            emit(AvatarEvents.PlayerLeft) { it.invoke(player) }
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
                val player = playerList?.get(packet.uuid)
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
                    headYaw = Look.radFromDeg(packet.headYaw.toDegrees())
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
                        entities.remove(eid)?.apply { playerList?.get(uuid)?.entity = null }
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
                getEntityOrCreate(packet.entityId)
                    .look = Look.fromDegrees(packet.yaw, packet.pitch)
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
                    emit(AvatarEvents.PlayerEntityStatusChanged) { it.invoke() }
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

            // XXX emit WindowReady, WindowClosed, SlotsChanged, WindowPropertyChanged
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
                when (val value = packet.value) {
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
            val timer = fixedRateTimer(period = 50) { doTick() }
            // bind timer lifetime to coroutineContext
            try {
                suspendCancellableCoroutine { } // wait until ticker is cancelled
            } finally {
                logger.fine("Cancelling tick timer")
                timer.cancel()
            }
        }
    }

    private fun doTick() {
        val prevPos = position
        val prevLook = look

        emit(AvatarEvents.PreClientTick) { it.invoke() }

        if (position != null) behavior.doPhysicsTick()

        if (position != null && look != null) {
            if (position != prevPos) {
                if (look != prevLook) {
                    send(ClientPlayerPositionRotationPacket(
                        onGround ?: true,
                        position!!.x,
                        position!!.y,
                        position!!.z,
                        look!!.yawDegrees().toFloat(),
                        look!!.pitchDegrees().toFloat()))
                } else {
                    send(ClientPlayerPositionPacket(
                        onGround ?: true,
                        position!!.x,
                        position!!.y,
                        position!!.z))
                }
            } else {
                if (look != prevLook) {
                    send(ClientPlayerRotationPacket(
                        onGround ?: true,
                        look!!.yawDegrees().toFloat(),
                        look!!.pitchDegrees().toFloat()))
                } else {
                    send(ClientPlayerMovementPacket((onGround ?: true)))
                }
            }
        }

        // TODO check chat buffer

        emit(AvatarEvents.ClientTick) { it.invoke() }
    }

    override fun packetSending(event: PacketSendingEvent) = Unit
    override fun packetSent(event: PacketSentEvent) = Unit

    override fun toString(): String {
        val connStatus = if (connected) "online" else "offline"
        return "AvatarImpl($identifier $connStatus at $position behavior=${behavior.name})"
    }
}

private val HandledByProtoLib = Unit
private val TodoInventoryPacket = Unit // TODO handle inventory packets
private val TodoEntityPacket = Unit // TODO handle entity packets
