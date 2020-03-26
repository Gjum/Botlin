package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.api.AvatarEvent
import com.github.gjum.minecraft.botlin.data.MinecraftData
import com.github.gjum.minecraft.botlin.impl.*
import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack
import com.github.steveice10.mc.protocol.data.game.window.WindowType
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.UUID

class InventoryTest {
	val mcData = MinecraftData("mcdata/1.12.2")
	val gameProfile = GameProfile(UUID.randomUUID(), "Test")

	@Test
	fun windowReadyTest() = runBlocking {
		val eventBoard = EventBoardImpl<AvatarEvent>(this)
		val avatar = MutableAvatar(gameProfile, mcData, eventBoard)

		Assertions.assertFalse(avatar.window.ready)

		avatar.handleServerPacket(ServerWindowItemsPacket(
			PLAYER_WINDOW_ID, Array(9 + 36 + 1) { ItemStack(0) }))
		avatar.handleServerPacket(ServerSetSlotPacket(
			CURSOR_WINDOW_ID, CURSOR_SLOT_NR, ItemStack(0)))

		Assertions.assertTrue(avatar.window.ready)

		avatar.handleServerPacket(ServerOpenWindowPacket(
			1, WindowType.CHEST, "Test Chest", 27))

		Assertions.assertFalse(avatar.window.ready)

		avatar.handleServerPacket(ServerWindowItemsPacket(
			1, Array(27 + 36) { ItemStack(0) }))
		avatar.handleServerPacket(ServerSetSlotPacket(
			CURSOR_WINDOW_ID, CURSOR_SLOT_NR, ItemStack(0)))

		Assertions.assertTrue(avatar.window.ready)

		eventBoard.disable()
	}

	@Test
	fun retainSlotsTest() = runBlocking {
		val eventBoard = EventBoardImpl<AvatarEvent>(this)
		val avatar = MutableAvatar(gameProfile, mcData, eventBoard)
		val slotsInv = Array(9 + 36 + 1) { ItemStack(0) }
		slotsInv[20] = ItemStack(1, 3, 2)
		avatar.handleServerPacket(ServerWindowItemsPacket(
			PLAYER_WINDOW_ID, slotsInv))
		avatar.handleServerPacket(ServerSetSlotPacket(
			CURSOR_WINDOW_ID, CURSOR_SLOT_NR, ItemStack(0)))

		Assertions.assertTrue(avatar.window.ready)
		Assertions.assertEquals(MutableSlot(20, mcData.items[1]!!, 2, 3, null), avatar.window.slots[20])

		avatar.handleServerPacket(ServerOpenWindowPacket(
			1, WindowType.CHEST, "Test Chest", 27))
		val slotsChest = Array(27 + 36) { ItemStack(0) }
		slotsChest[10] = ItemStack(2, 3)
		avatar.handleServerPacket(ServerWindowItemsPacket(
			1, slotsChest))
		avatar.handleServerPacket(ServerSetSlotPacket(
			CURSOR_WINDOW_ID, CURSOR_SLOT_NR, ItemStack(0)))

		Assertions.assertTrue(avatar.window.ready)
		Assertions.assertEquals(MutableSlot(10, mcData.items[2]!!, 0, 3, null), avatar.window.slots[10])

		eventBoard.disable()
	}
}
