package com.github.gjum.minecraft.botlin.impl

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.data.MinecraftData
import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.data.game.PlayerListEntryAction
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility
import com.github.steveice10.mc.protocol.data.game.world.notify.ClientNotification
import com.github.steveice10.mc.protocol.data.game.world.notify.ThunderStrengthValue
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientSettingsPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPlayerListEntryPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.*
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerChangeHeldItemPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerHealthPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerSetExperiencePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.*
import com.github.steveice10.mc.protocol.packet.ingame.server.window.*
import com.github.steveice10.mc.protocol.packet.ingame.server.world.*
import com.github.steveice10.packetlib.packet.Packet
import java.util.UUID
import java.util.logging.Logger

class MutableAvatar(
	override val profile: GameProfile,
	val mcData: MinecraftData,
	private val eventBoard: EventBoardImpl<AvatarEvent>
) : Avatar {
	private val logger = Logger.getLogger(this::class.java.name)

	override var world: MutableWorld? = null
	override var playerEntity: MutablePlayerEntity? = null
	override val playerList = mutableMapOf<UUID, MutablePlayerListItem>()
	var playerWindow = makePlayerWindow()
	override var window: MutableWindow = playerWindow
	override var hotbarSelection: Int? = null
	var settings = ClientSettingsPacket("en_us", 10, ChatVisibility.FULL, true, emptyArray(), Hand.MAIN_HAND) // TODO expose client settings in interface

	/**
	 * Call this before connecting again to mark fields as unset.
	 */
	fun reset() = synchronized(this) {
		playerEntity = null
		world = null
		playerList.clear()
		playerWindow = makePlayerWindow()
		window = playerWindow
		hotbarSelection = null
	}

	private inline fun <reified E : AvatarEvent> emit(e: E) {
		eventBoard.postAsync(e)
	}

	private fun getEntity(eid: Int): MutableEntity? {
		return world!!.entities[eid]
	}

	private fun getEntityOrCreate(eid: Int, create: () -> MutableEntity): MutableEntity {
		return world!!.entities.getOrPut(eid, create)
	}

	private fun getPlayerEntityOrCreate(eid: Int): MutablePlayerEntity {
		var player = getEntityOrCreate(eid) { MutablePlayerEntity(eid) }
		if (player !is MutablePlayerEntity) {
			player = MutablePlayerEntity(player)
			world!!.entities[eid] = player
		}
		return player
	}

	private fun makePlayerWindow() = MutableWindow(PLAYER_WINDOW_ID, null, "Player", 0, mcData.windows[null]!!)

	fun handleClientPacket(packet: Packet) = synchronized(this) {
		// XXX
	}

	fun handleServerPacket(packet: Packet) = synchronized(this) {
		handleServerPacketPlayer(packet)
		handleServerPacketEntitySpawn(packet)
		handleServerPacketEntityAction(packet)
		handleServerPacketEntityProps(packet)
		handleServerPacketInventory(packet)
		handleServerPacketBlocks(packet)
		handleServerPacketWorld(packet)
	}

	private fun handleServerPacketPlayer(packet: Packet) {
		when (packet) {
			is ServerJoinGamePacket -> {
				playerList.clear()
				world = MutableWorld(packet.dimension)
				playerEntity = getPlayerEntityOrCreate(packet.entityId)
					.apply { uuid = profile.id }
				playerEntity!!.gameMode = packet.gameMode
			}
			is ServerRespawnPacket -> {
				world = MutableWorld(packet.dimension)
				playerEntity = getPlayerEntityOrCreate(playerEntity!!.eid)
					.apply {
						gameMode = packet.gameMode
						health = null
						food = null
						saturation = null
						playerWindow = makePlayerWindow()
						window = playerWindow
					}
				// TODO respawn initiation event
			}
			is ServerPlayerHealthPacket -> {
				playerEntity?.apply {
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

					if (!wasIngame && ingame) emit(AvatarEvent.Spawned(playerEntity!!))
				}
			}
			is ServerPlayerSetExperiencePacket -> {
				val prevExp = playerEntity?.experience
				val wasIngame = ingame
				playerEntity?.experience = Experience(
					packet.slot,
					packet.level,
					packet.totalExperience
				)
				emit(AvatarEvent.ExpChanged(playerEntity?.experience!!, prevExp))
				if (!wasIngame && ingame) emit(AvatarEvent.Spawned(playerEntity!!))
			}
			is ServerPlayerPositionRotationPacket -> {
				val wasIngame = ingame
				val oldPosition = playerEntity!!.position

				var (x, y, z) = packet.run { Triple(x, y, z) }
				var (yaw, pitch) = packet.run { Pair(yaw.toDouble(), pitch.toDouble()) }

				// update relative fields to absolute
				for (rel in packet.relativeElements) {
					when (rel) {
						PositionElement.X -> x += oldPosition!!.x
						PositionElement.Y -> y += oldPosition!!.y
						PositionElement.Z -> z += oldPosition!!.z
						PositionElement.YAW -> yaw += playerEntity!!.look!!.yawDegrees
						PositionElement.PITCH -> pitch += playerEntity!!.look!!.pitchDegrees
						null -> error("Unexpected: relative coordinate key was null. In $packet")
					}
				}

				playerEntity?.position = Vec3d(x, y, z)
				playerEntity?.look = Look(yaw.radFromDeg(), pitch.radFromDeg())

				emit(AvatarEvent.TeleportedByServer(playerEntity!!.position!!, oldPosition, packet))

				if (!wasIngame && ingame) emit(AvatarEvent.Spawned(playerEntity!!))
			}
			is ServerVehicleMovePacket -> {
				val vehicle = playerEntity?.vehicle ?: error("Server moved bot while not in vehicle")
				vehicle.apply {
					val oldPosition = position
					position = Vec3d(packet.x, packet.y, packet.z)
					look = Look.fromDegrees(packet.yaw, packet.pitch)
					playerEntity?.position = position // TODO update client pos in relation to vehicle (typically up/down)
					emit(AvatarEvent.TeleportedByServer(position!!, oldPosition, packet))
				}
			}
		}
	}

	private fun handleServerPacketEntitySpawn(packet: Packet) {
		when (packet) {
			is ServerSpawnPlayerPacket -> {
				val entity = getPlayerEntityOrCreate(packet.entityId)
				entity.apply {
					uuid = packet.uuid
					position = Vec3d(packet.x, packet.y, packet.z)
					look = Look.fromDegrees(packet.yaw, packet.pitch)
					updateMetadata(packet.metadata)
				}
				val player = playerList.get(packet.uuid)
				if (player == null) {
					logger.warning("SpawnPlayer: unknown uuid ${packet.uuid} for eid ${packet.entityId}")
				} else {
					entity.playerListItem = player
					player.entity = entity
				}
			}
			is ServerSpawnObjectPacket -> {
				getEntityOrCreate(packet.entityId) {
					MutableObjectEntity(packet.entityId, packet.type, packet.data)
				}.apply {
					uuid = packet.uuid
					position = Vec3d(packet.x, packet.y, packet.z)
					look = Look.fromDegrees(packet.yaw, packet.pitch)
					velocity = Vec3d(packet.motionX, packet.motionY, packet.motionZ)
				}
			}
			is ServerSpawnMobPacket -> {
				getEntityOrCreate(packet.entityId) {
					MutableMobEntity(packet.entityId, packet.type)
				}.apply {
					uuid = packet.uuid
					position = Vec3d(packet.x, packet.y, packet.z)
					look = Look.fromDegrees(packet.yaw, packet.pitch)
					headYaw = packet.headYaw.radFromDeg()
					velocity = Vec3d(packet.motionX, packet.motionY, packet.motionZ)
					updateMetadata(packet.metadata)
				}
			}
			is ServerSpawnPaintingPacket -> {
				getEntityOrCreate(packet.entityId) {
					MutablePaintingEntity(packet.entityId, packet.paintingType, packet.direction)
				}.apply {
					uuid = packet.uuid
					position = packet.position.run { Vec3i(x, y, z).asVec3d }
				}
			}
			is ServerSpawnExpOrbPacket -> {
				getEntityOrCreate(packet.entityId) {
					MutableExpEntity(packet.entityId, packet.exp)
				}.apply {
					position = Vec3d(packet.x, packet.y, packet.z)
				}
			}
			is ServerSpawnGlobalEntityPacket -> {
				getEntityOrCreate(packet.entityId) {
					MutableGlobalEntity(packet.entityId, packet.type)
				}.apply {
					position = Vec3d(packet.x, packet.y, packet.z)
				}
			}
			is ServerEntityDestroyPacket -> {
				world?.entities?.also { entities ->
					for (eid in packet.entityIds) {
						entities.remove(eid)?.apply {
							playerList[uuid]?.entity = null
						}
					}
				}
			}
		}
	}

	private fun handleServerPacketEntityAction(packet: Packet) {
		when (packet) {
			is ServerEntityVelocityPacket -> {
				getEntity(packet.entityId)?.velocity = Vec3d(
					packet.motionX, packet.motionY, packet.motionZ
				)
			}
			is ServerEntityTeleportPacket -> {
				getEntity(packet.entityId)?.apply {
					position = Vec3d(packet.x, packet.y, packet.z)
					look = Look.fromDegrees(packet.yaw, packet.pitch)
					onGround = packet.isOnGround
				}
			}
			is ServerEntityPositionPacket -> {
				getEntity(packet.entityId)?.position = Vec3d(
					packet.movementX / (128 * 32),
					packet.movementY / (128 * 32),
					packet.movementZ / (128 * 32)
				)
			}
			is ServerEntityPositionRotationPacket -> {
				val entity = getEntity(packet.entityId) ?: return
				entity.position = Vec3d(
					packet.movementX,
					packet.movementY,
					packet.movementZ
				)
				entity.look = Look.fromDegrees(packet.yaw, packet.pitch)
			}
			is ServerEntityRotationPacket -> {
				getEntity(packet.entityId)?.look = Look.fromDegrees(packet.yaw, packet.pitch)
			}
			is ServerEntityHeadLookPacket -> {
				getEntity(packet.entityId)?.headYaw = packet.headYaw.radFromDeg()
			}
			is ServerEntityAttachPacket -> {
				getEntity(packet.entityId)?.vehicle = getEntity(packet.attachedToId)
				// TODO attach vice versa?
			}
			is ServerEntitySetPassengersPacket -> {
				val entity = getEntity(packet.entityId) ?: return
				val passengers = (packet.passengerIds)
					.map(this::getEntity)
					.filterNotNull()
					.toTypedArray()
				entity.passengers = passengers.asList()
				passengers.forEach { it.vehicle = entity }
			}
			is ServerEntityAnimationPacket -> TodoEntityPacket
			is ServerEntityCollectItemPacket -> TodoEntityPacket
		}
	}

	private fun handleServerPacketEntityProps(packet: Packet) {
		when (packet) {
			is ServerEntityMetadataPacket -> {
				val entity = getEntity(packet.entityId) ?: return
				entity.updateMetadata(packet.metadata)
				if (entity === playerEntity) {
					emit(AvatarEvent.PlayerEntityStatusChanged())
				}
			}
			is ServerEntityPropertiesPacket -> TodoEntityPacket // TODO emit PlayerEntityStatusChanged
			is ServerEntityEquipmentPacket -> TodoEntityPacket
			is ServerEntityEffectPacket -> TodoEntityPacket // TODO emit PlayerEntityStatusChanged
			is ServerEntityRemoveEffectPacket -> TodoEntityPacket // TODO emit PlayerEntityStatusChanged
			is ServerEntityStatusPacket -> TodoEntityPacket // TODO emit PlayerEntityStatusChanged
		}
	}

	private fun handleServerPacketInventory(packet: Packet) {
		when (packet) {
			is ServerPlayerChangeHeldItemPacket -> {
				hotbarSelection = packet.slot
			}
			is ServerOpenWindowPacket -> {
				val oldWindow = window
				val windowInfo = mcData.windows[packet.type]
					?: error("Unknown window type ${packet.type}")
				window = MutableWindow(packet.windowId, packet.type, packet.name, packet.slots, windowInfo)
				emit(AvatarEvent.WindowClosed(oldWindow))
			}
			is ServerCloseWindowPacket -> {
				val oldWindow = window
				window = playerWindow
				window.ready = false
				emit(AvatarEvent.WindowClosed(oldWindow))
			}
			is ServerWindowItemsPacket -> {
				if (packet.windowId != window.windowId
					&& packet.windowId == PLAYER_WINDOW_ID) {
					// server is addressing the player window without opening it
					emit(AvatarEvent.WindowClosed(window))
					window = playerWindow
					window.ready = false
				}
				if (packet.windowId == window.windowId) {
					val indices = packet.items.indices
					val oldSlots = window.slots.map { it.copy() }
					for ((i, itemStack) in packet.items.withIndex()) {
						window.slots[i].updateFromStack(itemStack, mcData)
					}
					val newSlots = window.slots
					emit(AvatarEvent.SlotsChanged(window, indices, oldSlots, newSlots))
				}
			}
			is ServerSetSlotPacket -> {
				if (packet.windowId != window.windowId
					&& packet.windowId == PLAYER_WINDOW_ID) {
					// server is addressing the player window without opening it
					emit(AvatarEvent.WindowClosed(window))
					window = playerWindow
					window.ready = false
				}
				val indices = IntRange(packet.slot, packet.slot)
				if (packet.windowId == CURSOR_WINDOW_ID && packet.slot == CURSOR_SLOT_NR) {
					val oldSlots = listOf(window.cursorSlot.copy())
					window.cursorSlot.updateFromStack(packet.item, mcData)
					val newSlots = listOf(window.cursorSlot)
					emit(AvatarEvent.SlotsChanged(window, indices, oldSlots, newSlots))

					if (!window.ready) {
						// window initialization order: WindowItems, cursor slot, non-empty slots as SetSlot
						// so at this point, the window is in sync
						window.ready = true
						emit(AvatarEvent.WindowReady(window))
					}
				} else if (packet.windowId == window.windowId) {
					val oldSlots = window.slots.slice(indices).map { it.copy() }
					window.slots[packet.slot].updateFromStack(packet.item, mcData)
					val newSlots = window.slots.slice(indices)
					emit(AvatarEvent.SlotsChanged(window, indices, oldSlots, newSlots))
				}
			}
			is ServerWindowPropertyPacket -> {
				if (packet.windowId == window.windowId) {
					val oldValue = window.properties.put(packet.rawProperty, packet.value)
					emit(AvatarEvent.WindowPropertyChanged(window, packet.rawProperty, oldValue, packet.value))
				}
			}
			is ServerConfirmTransactionPacket -> {
				if (!packet.accepted) window.ready = false // expecting server to update all slots now
				emit(AvatarEvent.TransactionResponse(packet.windowId, packet.actionId, packet.accepted))
			}
		}
	}

	private fun handleServerPacketBlocks(packet: Packet) {
		// TODO block change events
		when (packet) {
			is ServerChunkDataPacket -> {
				val column = packet.column
				world?.updateColumn(column.x, column.z, column)
			}
			is ServerUnloadChunkPacket -> {
				world?.unloadColumn(packet.x, packet.z)
			}
			is ServerBlockChangePacket -> {
				val change = packet.record
				world?.setBlockState(change.position, change.block)
			}
			is ServerMultiBlockChangePacket -> {
				for (change in packet.records) {
					world?.setBlockState(change.position, change.block)
				}
			}
			is ServerBlockValuePacket -> {
				packet.apply {
					// TODO BlockValuePacket
				}
			}
		}
	}

	private fun handleServerPacketWorld(packet: Packet) {
		when (packet) {
			is ServerChatPacket -> {
				emit(AvatarEvent.ChatReceived(packet.message))
			}
			is ServerPlayerListEntryPacket -> {
				for (item in packet.entries) {
					val wasInListBefore = item.profile.id in playerList
					val player = playerList.getOrPut(item.profile.id) { MutablePlayerListItem(item.profile) }
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
						playerList.remove(item.profile.id)
						if (wasInListBefore) {
							emit(AvatarEvent.PlayerLeft(player))
						}
					}
				}
			}
			is ServerNotifyClientPacket -> {
				when (packet.notification) {
					ClientNotification.START_RAIN -> world?.rainy = false
					ClientNotification.STOP_RAIN -> world?.rainy = true
					else -> Unit
				}
				when (val value = packet.value) {
					is GameMode -> playerEntity?.gameMode = value
					is ThunderStrengthValue -> world?.skyDarkness = value.strength.toDouble()
					// TODO track other world states
				}
			}
		}
	}
}

private val TodoEntityPacket = Unit // TODO handle entity packets
