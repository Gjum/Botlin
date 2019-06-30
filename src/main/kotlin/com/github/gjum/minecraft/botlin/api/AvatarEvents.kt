package com.github.gjum.minecraft.botlin.api

import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.packet.Packet

interface AvatarEvent : Event

// TODO add all events missing to make the spectator module
object AvatarEvents {
    /** [Avatar.connected] switched from false to true. */
    data class Connected(val connection: Session) : AvatarEvent

    /**
     * [Avatar.connected] switched from true to false.
     * This happens when the client intentionally disconnects,
     * when the server disconnects the client, when a network error occurs, etc.
     * Check the reason/cause to differentiate between these.
     */
    data class Disconnected(val connection: Session, val reason: String?, val cause: Any?) : AvatarEvent

    /** [Avatar.spawned] switched from false to true. */
    data class Spawned(val entity: Entity) : AvatarEvent

    data class ServerPacketReceived(val packet: Packet) : AvatarEvent

    data class ChatReceived(val msg: Message) : AvatarEvent

    /**
     * Called every 50ms before the [Avatar] processes its tick actions
     * (physics, chat sending, etc.). See also [ClientTick].
     */
    class PreClientTick : AvatarEvent

    /**
     * Called every 50ms after the [Avatar] is done processing its tick actions
     * (physics, chat sending, etc.). See also [PreClientTick].
     */
    class ClientTick : AvatarEvent

    /**
     * An entry was created on the [Avatar.playerList].
     *
     * On some servers, [PlayerLeft] and [PlayerJoined] may occur
     * quickly after one another shortly after joining the server.
     */
    data class PlayerJoined(val entry: PlayerListItem) : AvatarEvent

    /**
     * An entry was removed from the [Avatar.playerList].
     *
     * On some servers, [PlayerLeft] and [PlayerJoined] may occur
     * quickly after one another shortly after joining the server.
     */
    data class PlayerLeft(val entry: PlayerListItem) : AvatarEvent

    /**
     * The [Avatar.inventory] has received all state. This happens typically
     * after a window was opened, but may also happen after sending an
     * invalid click action to the server, to synchronize client and server again.
     * An ongoing synchronization can be checked through [Window.ready].
     */
    data class WindowReady(val newWindow: Window) : AvatarEvent

    /**
     * The current window was closed by the server.
     * The new Window will be in the next [WindowReady].
     */
    data class WindowClosed(val oldWindow: Window) : AvatarEvent

    /**
     * The position was forcefully changed by the server.
     * This happens after joining a server,
     * moving illegally (e.g., into a block or too quickly),
     * or respawning after death or in another dimension.
     */
    data class TeleportedByServer(val newPos: Vec3d, val oldPos: Vec3d?, val reason: Any?) : AvatarEvent

    data class HealthChanged(val new: Float, val old: Float?) : AvatarEvent

    data class FoodChanged(val new: Int, val old: Int?) : AvatarEvent

    data class SaturationChanged(val new: Float, val old: Float?) : AvatarEvent

    data class ExpChanged(val new: Experience, val old: Experience?) : AvatarEvent

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
    data class SlotsChanged(
        val window: Window,
        val indices: List<Int>,
        val newSlots: List<Slot>,
        val oldSlots: List<Slot>
    ) : AvatarEvent

    data class WindowPropertyChanged(val window: Window, val property: Int, val oldValue: Int, val newValue: Int) : AvatarEvent

    class PlayerEntityStatusChanged : AvatarEvent // TODO PlayerEntityStatusChanged args? split into several events?
}
