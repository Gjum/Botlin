package com.github.gjum.minecraft.botlin.api

import com.github.gjum.minecraft.botlin.state.*
import com.github.gjum.minecraft.botlin.util.Vec3d
import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.packet.Packet

interface Event<T, E>

private typealias AvatarEvent<T> = Event<T, AvatarEvents>

object AvatarEvents {
    /** [Avatar.connected] switched from false to true. */
    @JvmStatic
    val Connected = object : AvatarEvent<(connection: Session) -> Unit> {}

    /**
     * [Avatar.connected] switched from true to false.
     * This happens when the client intentionally disconnects,
     * when the server disconnects the client, when a network error occurs, etc.
     * Check the reason/cause to differentiate between these.
     */
    @JvmStatic
    val Disconnected = object : AvatarEvent<(connection: Session, reason: String?, cause: Any?) -> Unit> {}

    /** [Avatar.spawned] switched from false to true. */
    @JvmStatic
    val Spawned = object : AvatarEvent<(entity: Entity) -> Unit> {}

    @JvmStatic
    val ServerPacketReceived = object : AvatarEvent<(packet: Packet) -> Unit> {}

    @JvmStatic
    val ChatReceived = object : AvatarEvent<(msg: Message) -> Unit> {}

    /**
     * Called every 50ms before the [Avatar] processes its tick actions
     * (physics, chat sending, etc.). See also [ClientTick].
     */
    @JvmStatic
    val PreClientTick = object : AvatarEvent<() -> Unit> {}

    /**
     * Called every 50ms after the [Avatar] is done processing its tick actions
     * (physics, chat sending, etc.). See also [PreClientTick].
     */
    @JvmStatic
    val ClientTick = object : AvatarEvent<() -> Unit> {}

    /**
     * An entry was created on the [Avatar.playerList].
     *
     * On some servers, [PlayerLeft] and [PlayerJoined] may occur
     * quickly after one another shortly after joining the server.
     */
    @JvmStatic
    val PlayerJoined = object : AvatarEvent<(entry: PlayerListItem) -> Unit> {}

    /**
     * An entry was removed from the [Avatar.playerList].
     *
     * On some servers, [PlayerLeft] and [PlayerJoined] may occur
     * quickly after one another shortly after joining the server.
     */
    @JvmStatic
    val PlayerLeft = object : AvatarEvent<(entry: PlayerListItem) -> Unit> {}

    /**
     * The [Avatar.inventory] has received all state. This happens typically
     * after a window was opened, but may also happen after sending an
     * invalid click action to the server, to synchronize client and server again.
     * An ongoing synchronization can be checked through [Window.ready].
     */
    @JvmStatic
    val WindowReady = object : AvatarEvent<(newWindow: Window) -> Unit> {}

    /**
     * The current window was closed by the server.
     * The new Window will be in the next [WindowReady].
     */
    @JvmStatic
    val WindowClosed = object : AvatarEvent<(oldWindow: Window) -> Unit> {}

    /**
     * The position was forcefully changed by the server.
     * This happens after joining a server,
     * moving illegally (e.g., into a block or too quickly),
     * or respawning after death or in another dimension.
     */
    @JvmStatic
    val TeleportByServer = object : AvatarEvent<(newPos: Vec3d, oldPos: Vec3d?, reason: Any?) -> Unit> {}

    @JvmStatic
    val HealthChanged = object : AvatarEvent<(new: Float, old: Float?) -> Unit> {}

    @JvmStatic
    val FoodChanged = object : AvatarEvent<(new: Int, old: Int?) -> Unit> {}

    @JvmStatic
    val SaturationChanged = object : AvatarEvent<(new: Float, old: Float?) -> Unit> {}

    @JvmStatic
    val ExpChanged = object : AvatarEvent<(new: Experience, old: Experience?) -> Unit> {}

    /**
     * The slots at the indices were changed by the server.
     * This happens typically when picking up items,
     * receiving state of a newly opened window, or
     * after sending an invalid click action to the server,
     * to synchronize client and server again.
     * In the latter cases, [WindowReady] will be fired
     * once everything is synchronized with the server.
     * An ongoing synchronization can be checked through [Window.ready].
     */
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
