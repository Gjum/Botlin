package com.github.gjum.minecraft.botlin.defaults

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.util.normalizeServerAddress
import com.github.gjum.minecraft.botlin.util.splitHostPort
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
import kotlinx.coroutines.*
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.fixedRateTimer

// TODO catch handler exceptions

private val brandBytesVanilla = byteArrayOf(7, 118, 97, 110, 105, 108, 108, 97)

class AvatarModule : Module() {
	override val name = "AvatarModule"

	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		val args = serviceRegistry.consumeService(MainArgs::class.java)?.args
		val serverAddress = normalizeServerAddress(
			args?.getOrNull(1)
				?: System.getProperty("serverAddress")
				?: System.getenv("MINECRAFT_SERVER_ADDRESS")
				?: "localhost")

		val auth = serviceRegistry.consumeService(Authentication::class.java)!!
		val (proto, authError) = auth.authenticate()
		if (proto == null) throw IllegalArgumentException("Failed to setup Avatar: $authError")
		val profile = proto.profile
		Logger.getLogger(javaClass.name).fine("Using profile ${profile.name} ${profile.idAsString}")
		val avatar = AvatarImpl(serviceRegistry, profile, serverAddress)
		serviceRegistry.provideService(Avatar::class.java, avatar)
	}
}

class AvatarImpl(
    override val serviceRegistry: ServiceRegistry,
    override val profile: GameProfile,
    serverAddr: String
) : Avatar, SessionListener, EventEmitterImpl<AvatarEvent>(), CoroutineScope by serviceRegistry {
    private val logger = Logger.getLogger(this::class.java.name)
    private var ticker: Job? = null

    override val serverAddress = normalizeServerAddress(serverAddr)

    override var connection: Session? = null
    override var endReason: String? = null

    override var entity: Entity? = null
    override var health: Float? = null
    override var food: Int? = null
    override var saturation: Float? = null
    override var experience: Experience? = null

    override var inventory: Window? = null
    override var world: World? = null
    override var playerList: MutableMap<UUID, PlayerListItem>? = null

    override var position: Vec3d?
        get() = entity?.position
        set(value) {
            entity?.position = value
        }

    override var look: Look?
        get() = entity?.look
        set(value) {
            entity?.look = value
        }

    override val onGround: Boolean? get() = entity?.onGround

    override var gameMode: GameMode?
        get() = entity?.playerListItem?.gameMode
        set(v) {
            if (entity?.playerListItem == null) {
                entity?.playerListItem = PlayerListItem(profile)
            }
            entity?.playerListItem?.gameMode = v
        }

    private fun send(packet: Packet) = connection?.send(packet)

    private fun reset() {
        endReason = null

        ticker?.cancel()
        ticker = null

        entity = null
        health = null
        food = null
        saturation = null
        experience = null
        inventory = null

        world = null
        playerList = null
    }

    private fun getEntityOrCreate(eid: Int): Entity {
        return world!!.entities.getOrPut(eid) { Entity(eid) }
    }

    @Synchronized
    override fun connect(proto: MinecraftProtocol) {
        if (connection != null) TODO("already connected") // bail? close existing and use new one?
        val (host, port) = splitHostPort(serverAddress)
        logger.fine("Connecting as ${profile.name} to $host $port")
        val client = Client(host, port, proto, TcpSessionFactory())
        reset()
        connection = client.session
        client.session.addListener(this)
        client.session.connect(true)
    }

    override fun connected(event: ConnectedEvent) {
        emit(AvatarEvent.Connected(event.session))
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
    @Synchronized
    override fun disconnect(reason: String?, cause: Throwable?) {
        val c = connection ?: return
        if (endReason == null) {
            emit(AvatarEvent.Disconnected(c, reason, cause))
        }
        // reset() // TODO do we already reset here or remember the fail state?
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
        emit(AvatarEvent.ServerPacketReceived(packet))
    }

    private fun processPacket(packet: Packet) {
        when (packet) {
            is ServerChatPacket -> {
                emit(AvatarEvent.ChatReceived(packet.message))
            }
            is ServerJoinGamePacket -> {
                playerList = mutableMapOf()
                world = World(packet.dimension)
                entity = getEntityOrCreate(packet.entityId).apply { uuid = profile.id }
                gameMode = packet.gameMode
                send(ClientPluginMessagePacket("MC|Brand", brandBytesVanilla))
                send(ClientSettingsPacket("en_us", 10, ChatVisibility.FULL, true, emptyArray(), Hand.MAIN_HAND))
            }
            is ServerRespawnPacket -> {
                val eid = entity!!.eid
                world = World(packet.dimension)
                entity = getEntityOrCreate(eid).apply { uuid = profile.id }
                gameMode = packet.gameMode
                health = null
                food = null
                saturation = null
                inventory = null
                // TODO respawn initiation event
            }
            is ServerPlayerHealthPacket -> {
                val prevHealth = health
                val prevFood = food
                val prevSaturation = saturation

                val wasIngame = ingame

                health = packet.health
                food = packet.food
                saturation = packet.saturation

                if (prevHealth != health) emit(AvatarEvent.HealthChanged(health!!, prevHealth))
                if (prevFood != food) emit(AvatarEvent.FoodChanged(food!!, prevFood))
                if (prevSaturation != saturation) emit(AvatarEvent.SaturationChanged(saturation!!, prevSaturation))

                if (!wasIngame && ingame) emit(AvatarEvent.Spawned(entity!!))
            }
            is ServerPlayerSetExperiencePacket -> {
                val prevExp = experience
                val wasIngame = ingame
                experience = Experience(
                    packet.slot,
                    packet.level,
                    packet.totalExperience
                )
                emit(AvatarEvent.ExpChanged(experience!!, prevExp))
                if (!wasIngame && ingame) emit(AvatarEvent.Spawned(entity!!))
            }
            is ServerPlayerPositionRotationPacket -> {
                send(ClientTeleportConfirmPacket(packet.teleportId))
                val wasIngame = ingame
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

                emit(AvatarEvent.TeleportedByServer(position!!, oldPosition, packet))
                if (!wasIngame && ingame) emit(AvatarEvent.Spawned(entity!!))

                startTicker()
            }
            is ServerVehicleMovePacket -> {
                val vehicle = entity?.vehicle ?: error("Server moved bot while not in vehicle")
                vehicle.apply {
                    val oldPosition = position
                    position = Vec3d(packet.x, packet.y, packet.z)
                    look = Look.fromDegrees(packet.yaw, packet.pitch)
                    entity?.position = position // TODO update client pos in relation to vehicle (typically up/down)
                    emit(AvatarEvent.TeleportedByServer(position!!, oldPosition, packet))
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
                            emit(AvatarEvent.PlayerJoined(player))
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
                            emit(AvatarEvent.PlayerLeft(player))
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
                    emit(AvatarEvent.PlayerEntityStatusChanged())
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
        ticker = serviceRegistry.launch {
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

        emit(AvatarEvent.PreClientTick())

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

        emit(AvatarEvent.ClientTick())
    }

    override fun packetSending(event: PacketSendingEvent) = Unit
    override fun packetSent(event: PacketSentEvent) = Unit

    override fun toString(): String {
        val connStatus = if (connected) "online" else "offline"
        return "AvatarImpl($identifier $connStatus at $position)"
    }
}

private val HandledByProtoLib = Unit
private val TodoInventoryPacket = Unit // TODO handle inventory packets
private val TodoEntityPacket = Unit // TODO handle entity packets
