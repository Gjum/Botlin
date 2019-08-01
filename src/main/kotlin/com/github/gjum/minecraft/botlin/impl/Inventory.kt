package com.github.gjum.minecraft.botlin.impl

import com.github.gjum.minecraft.botlin.api.Slot
import com.github.gjum.minecraft.botlin.api.Window
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack
import com.github.steveice10.mc.protocol.data.game.window.WindowType
import com.github.steveice10.opennbt.tag.builtin.CompoundTag

data class MutableSlot(
	override var index: Int,
	override var itemId: Int,
	override var itemMeta: Int,
	override var amount: Int,
	override var nbtData: CompoundTag?
) : Slot {
	override val empty: Boolean get() = itemId <= 0 || amount <= 0
	override val maxStackSize: Int get() = TODO("look up stack size in mc-data")
	override val name: String get() = "TODO" // TODO look up slot name in mc-data
	override val customName: String? get() = null // TODO look up custom name in NBT

	override fun stacksWith(other: Slot): Boolean {
		if (itemId != other.itemId) return false
		if (itemMeta != other.itemMeta) return false
		if (maxStackSize <= 1) return false
		if (nbtData != other.nbtData) return false // TODO implement stacking correctly (NBT data comparison)
		return true
	}
}

fun MutableSlot.updateFromStack(itemStack: ItemStack) {
	itemId = itemStack.id
	amount = itemStack.amount
	nbtData = itemStack.nbt
}

fun MutableSlot.toStack() = ItemStack(itemId, amount, nbtData)

fun makeEmptySlot(index: Int) = MutableSlot(index, 0, 0, 0, null)

const val PLAYER_WINDOW_ID = 0
const val CURSOR_WINDOW_ID = -1
const val CURSOR_SLOT_NR = -1

class MutableWindow(
	override val windowId: Int,
	override val windowType: WindowType?, // null means player window
	override val windowTitle: String?
) : Window {
	override var ready = false
	override val slotCount = 0 // XXX window slotCount from mc-data
	var slotsMut = MutableList(slotCount + 36, ::makeEmptySlot)
	override val slots: List<Slot> get() = slotsMut
	override var cursorSlot = makeEmptySlot(CURSOR_SLOT_NR)
	override val properties = mutableMapOf<Int, Int>() // TODO access props by name, via mc-data

	override val hotbar: List<MutableSlot>
		get() {
			// special case for player window: last slot is offhand
			val end = if (windowId == PLAYER_WINDOW_ID) slots.size - 1 else slots.size
			return slotsMut.slice(end - 9 until end)
		}
	// TODO access special slots by name, via mc-data
}

fun makePlayerWindow() = MutableWindow(PLAYER_WINDOW_ID, null, null)
