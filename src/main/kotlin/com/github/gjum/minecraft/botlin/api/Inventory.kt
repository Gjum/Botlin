package com.github.gjum.minecraft.botlin.api

import com.github.gjum.minecraft.botlin.data.ItemInfo
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack
import com.github.steveice10.mc.protocol.data.game.window.WindowType
import com.github.steveice10.opennbt.tag.builtin.CompoundTag

interface Stack {
	val item: ItemInfo
	val meta: Int
	val amount: Int
	val nbtData: CompoundTag?

	val empty: Boolean get() = amount <= 0 || item.idNr <= 0
	val full: Boolean get() = amount >= maxStackSize

	val maxStackSize: Int get() = item.maxStackSize
	val displayName: String get() = item.variantNames[meta] ?: item.displayName
	val customName: String? get() = null // TODO look up custom name in NBT

	fun stacksWith(other: Stack): Boolean {
		if (item.idNr != other.item.idNr) return false
		if (meta != other.meta) return false
		if (maxStackSize <= 1) return false
		if (nbtData != other.nbtData) return false // TODO implement stacking correctly (NBT data comparison)
		return true
	}

	fun toStack() = ItemStack(item.idNr, meta, amount, nbtData)
}

interface Slot : Stack {
	val index: Int
}

interface Window {
	val windowId: Int
	/** null means player window */
	val windowType: WindowType?
	val windowTitle: String?
	val ready: Boolean
	val slotCount: Int
	val slots: List<Slot>
	val cursorSlot: Slot
	val properties: Map<Int, Int>
	// TODO access window props by name, via mc-data

	val hotbarStart: Int
	val hotbar: List<Slot>
	val inventoryStart: Int
	val inventory: List<Slot>

	fun isInHotbar(slotNr: Int) = slotNr - hotbarStart in 0..9
	fun isInInventory(slotNr: Int) = slotNr - inventoryStart in 0..(9 * 3)
}
