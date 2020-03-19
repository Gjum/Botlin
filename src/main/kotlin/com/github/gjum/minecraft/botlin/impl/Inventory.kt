package com.github.gjum.minecraft.botlin.impl

import com.github.gjum.minecraft.botlin.api.Slot
import com.github.gjum.minecraft.botlin.api.Stack
import com.github.gjum.minecraft.botlin.api.Window
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack
import com.github.steveice10.mc.protocol.data.game.window.WindowType
import com.github.steveice10.opennbt.tag.builtin.CompoundTag

open class MutableStack(
	itemIdInitially: Int,
	override var meta: Int,
	amountInitially: Int,
	override var nbtData: CompoundTag?
) : Stack {
	override var itemId = itemIdInitially
		set(value) {
			field = value
			if (value <= 0 && amount != 0) amount = 0 // empty slot
		}

	override var amount = amountInitially
		set(value) {
			field = value
			if (value <= 0 && itemId != 0) itemId = 0 // empty slot
		}

	override val maxStackSize: Int get() = TODO("look up stack size in mc-data")
	override val name: String get() = "TODO" // TODO look up slot name in mc-data
	override val customName: String? get() = null // TODO look up custom name in NBT
}

fun MutableStack.updateFrom(stack: Stack) {
	itemId = if (amount > 0) stack.itemId else 0
	meta = stack.meta
	amount = stack.amount
	nbtData = stack.nbtData
}

fun MutableStack.updateFromStack(itemStack: ItemStack?) {
	if (itemStack == null) {
		itemId = 0
		meta = 0
		amount = 0
		nbtData = null
	} else {
		itemId = if (amount > 0) itemStack.id else 0
		meta = itemStack.data
		amount = itemStack.amount
		nbtData = itemStack.nbt
	}
}

fun Stack.copy() = MutableStack(itemId, meta, amount, nbtData)

class MutableSlot(
	override val index: Int,
	itemId: Int,
	meta: Int,
	amount: Int,
	nbtData: CompoundTag?
) : Slot, MutableStack(itemId, meta, amount, nbtData)

fun Slot.copy() = MutableSlot(index, itemId, meta, amount, nbtData)

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
	override val slotCount = 10 // XXX window slotCount from mc-data
	override val slots = MutableList(slotCount + 36, ::makeEmptySlot)
	override var cursorSlot = makeEmptySlot(CURSOR_SLOT_NR)
	override val properties = mutableMapOf<Int, Int>()
	// TODO access props by name, via mc-data

	// special case for player window: last slot is offhand
	override val hotbarStart = if (windowId == PLAYER_WINDOW_ID) slots.size - 10 else slots.size - 9
	override val inventoryStart = hotbarStart - 9 * 3

	override val hotbar get() = slots.slice(hotbarStart until hotbarStart + 9)
	override val inventory get() = slots.slice(inventoryStart until hotbarStart)

	// TODO access special slots by name, via mc-data
}

fun makePlayerWindow() = MutableWindow(PLAYER_WINDOW_ID, null, null)
