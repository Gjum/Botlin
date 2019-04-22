package com.github.gjum.minecraft.botlin

import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowPropertyPacket
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.packet.Packet

val listenerInterfaces = listOf(
    IChatListener::class,
    IClientTickListener::class,
    IInventoryListener::class,
    IPacketListener::class,
    IPlayerListListener::class,
    IPlayerStateListener::class,
    IReadyListener::class
)

interface IChatListener {
    fun onChatReceived(msg: Message)
}

interface IClientTickListener {
    fun onPreClientTick()
    fun onPostClientTick()
}

interface IInventoryListener {
    /**
     * The window is open and all its slots were received.
     */
    fun onWindowReady(window: McWindow)

    /**
     * The current window is being closed.
     * Do not interact with the new window until it's ready.
     */
    fun onWindowClosed()

    fun onWindowPropertyChanged(property: ServerWindowPropertyPacket)

    fun onSlotsChanged() // TODO onSlotsChanged args?
}

interface IPacketListener {
    fun onServerPacketReceived(packet: Packet)
}

interface IPlayerListListener {
    /**
     * A player entry was added to the player list.
     */
    fun onPlayerJoined(entry: PlayerListItem)

    /**
     * A player entry was removed from the player list.
     */
    fun onPlayerLeft(entry: PlayerListItem)
}

interface IPlayerStateListener {
    fun onPositionChanged(position: Vec3d)
    fun onPlayerEntityStatusChanged() // TODO onPlayerEntityStatusChanged args?
}

interface IReadyListener {
    /**
     * Bot is connected to the game, but may not have received position, health, or inventory yet.
     */
    fun onConnected(connection: Session)

    /**
     * Bot has received position, health, and exp.
     */
    fun onSpawned()

    fun onDisconnected(reason: String?, cause: Throwable?)
}
