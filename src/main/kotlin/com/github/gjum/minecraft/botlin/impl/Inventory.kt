package com.github.gjum.minecraft.botlin.impl

import com.github.gjum.minecraft.botlin.api.Slot
import com.github.gjum.minecraft.botlin.api.Stack
import com.github.gjum.minecraft.botlin.api.Window
import com.github.gjum.minecraft.jmcdata.ItemInfo
import com.github.gjum.minecraft.jmcdata.MinecraftData
import com.github.gjum.minecraft.jmcdata.WindowInfo
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack
import com.github.steveice10.mc.protocol.data.game.window.WindowType
import com.github.steveice10.opennbt.tag.builtin.CompoundTag

val emptyItem = ItemInfo(-1, "(empty)", "Empty", 0, emptyMap(), null)

open class MutableStack(
	itemInitially: ItemInfo,
	override var meta: Int,
	amountInitially: Int,
	override var nbtData: CompoundTag?
) : Stack {
	override var item = itemInitially
		set(value) {
			field = value
			if (value.idNr <= 0 && amount != 0) amount = 0 // empty slot
		}

	override var amount = amountInitially
		set(value) {
			field = value
			if (value <= 0 && item.idNr != -1) item = emptyItem // empty slot
		}

	override fun toString() = "Stack{${amount}x $item $nbtData}"

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		other as MutableStack
		if (item.idNr != other.item.idNr) return false
		if (amount != other.amount) return false
		if (nbtData != other.nbtData) return false
		return true
	}

	override fun hashCode(): Int {
		var result = nbtData?.hashCode() ?: 0
		result = 256 * result + item.idNr
		result = 16 * result + meta
		result = 64 * result + amount
		return result
	}

	fun updateFrom(stack: Stack) {
		item = stack.item
		meta = stack.meta
		amount = stack.amount
		nbtData = stack.nbtData
	}

	fun updateFromStack(itemStack: ItemStack?, mcData: MinecraftData) {
		if (itemStack == null) {
			item = emptyItem
			meta = 0
			amount = 0
			nbtData = null
		} else {
			item = mcData.items[itemStack.id]
				?: error("Unknown item idNr: ${itemStack.id}")
			meta = itemStack.data
			amount = itemStack.amount
			nbtData = itemStack.nbt
		}
	}
}

fun Stack.copy() = MutableStack(item, meta, amount, nbtData)

class MutableSlot(
	override val index: Int,
	item: ItemInfo,
	meta: Int,
	amount: Int,
	nbtData: CompoundTag?
) : Slot, MutableStack(item, meta, amount, nbtData) {
	override fun toString() = "Slot{$index: ${amount}x $item nbt=$nbtData}"

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		other as MutableSlot
		if (index != other.index) return false
		return super.equals(other)
	}

	override fun hashCode(): Int {
		return index + 128 * super.hashCode()
	}
}

fun Slot.copy() = MutableSlot(index, item, meta, amount, nbtData)

fun makeEmptySlot(index: Int) = MutableSlot(index, emptyItem, 0, 0, null)

const val PLAYER_WINDOW_ID = 0
const val CURSOR_WINDOW_ID = -1
const val CURSOR_SLOT_NR = -1

class MutableWindow(
	override val windowId: Int,
	override val windowType: WindowType?,
	override val windowTitle: String,
	slotCount: Int,
	private val windowInfo: WindowInfo
) : Window {
	private val actualSlotCount = run {
		if (slotCount > 0) slotCount
		else if (windowType == null) 10 // player window, special case because of the offhand slot
		else windowInfo.slots.values.map(IntRange::last).max() ?: 0
	}
	override var ready = false
	override val slots = MutableList(actualSlotCount + 36, ::makeEmptySlot)
	override var cursorSlot = makeEmptySlot(CURSOR_SLOT_NR)
	override val properties = mutableMapOf<Int, Int>()

	override fun getProperty(name: String) = properties[windowInfo.properties.indexOf(name)]
		?: error("No such window property: '$name' in $windowType")

	override val isStorage = slotCount > 0

	// special case for player window: last slot is offhand
	override val hotbarStart = if (windowId == PLAYER_WINDOW_ID) slots.size - 10 else slots.size - 9
	override val inventoryStart = hotbarStart - 9 * 3

	override val hotbar get() = slots.slice(hotbarStart until hotbarStart + 9)
	override val inventory get() = slots.slice(inventoryStart until hotbarStart)

	override fun getSlotRange(name: String) = slots.slice(windowInfo.slots[name]
		?: error("No such slot range: '$name' in $windowType"))
}
