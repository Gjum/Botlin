package com.github.gjum.minecraft.botlin.api

import com.github.gjum.minecraft.botlin.state.Entity
import com.github.gjum.minecraft.botlin.state.PlayerListItem
import com.github.gjum.minecraft.botlin.state.Slot
import com.github.gjum.minecraft.botlin.state.Window
import com.github.gjum.minecraft.botlin.util.Vec3d
import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.packet.Packet

interface Event<T, E>

private typealias AvatarEvent<T> = Event<T, AvatarEvents>

object AvatarEvents {
    @JvmStatic
    val Connected = object : AvatarEvent<(connection: Session) -> Unit> {}
    @JvmStatic
    val Disconnected = object : AvatarEvent<(connection: Session, reason: String?, cause: Any?) -> Unit> {}
    @JvmStatic
    val Spawned = object : AvatarEvent<(entity: Entity) -> Unit> {}
    @JvmStatic
    val ServerPacketReceived = object : AvatarEvent<(packet: Packet) -> Unit> {}
    @JvmStatic
    val ChatReceived = object : AvatarEvent<(msg: Message) -> Unit> {}
    @JvmStatic
    val PreClientTick = object : AvatarEvent<() -> Unit> {}
    @JvmStatic
    val ClientTick = object : AvatarEvent<() -> Unit> {}
    @JvmStatic
    val PlayerJoined = object : AvatarEvent<(entry: PlayerListItem) -> Unit> {}
    @JvmStatic
    val PlayerLeft = object : AvatarEvent<(entry: PlayerListItem) -> Unit> {}
    @JvmStatic
    val WindowReady = object : AvatarEvent<(newWindow: Window) -> Unit> {}

    /** The new Window will be in the next WindowReady */
    @JvmStatic
    val WindowClosed = object : AvatarEvent<(oldWindow: Window) -> Unit> {}

    @JvmStatic
    val PositionChanged = object : AvatarEvent<(newPos: Vec3d, oldPos: Vec3d?, reason: Any?) -> Unit> {}

    @JvmStatic
    val SlotsChanged = object : AvatarEvent<(
        window: Window,
        indices: Array<Int>,
        newSlots: Array<Slot>,
        oldSlots: Array<Slot>) -> Unit> {}

    @JvmStatic
    val WindowPropertyChanged = object : AvatarEvent<(window: Window, property: Int, oldValue: Int, newValue: Int) -> Unit> {}

    @JvmStatic
    val PlayerEntityStatusChanged = object : AvatarEvent<() -> Unit> {} // TODO PlayerEntityStatusChanged args? split into several events?
}
