package com.github.gjum.minecraft.botlin

import com.github.steveice10.opennbt.NBTIO

class Slot(
    var blockId: Int,
    var itemCount: Int,
    var itemDamage: Int,
    var nbtData: NBTIO?
) {
    val empty: Boolean get() = blockId <= 0
    val name: String get() = "TODO" // XXX look up slot name in mc-data
    val customName: String? get() = "TODO" // XXX look up custom name in NBT
}

fun emptySlot() = Slot(0, 0, 0, null)

class McWindow(
    val windowId: Int,
    val inventoryType: String,
    val windowTitle: String,
    val slotCount: Int,
    val entityId: Int?
) {
    var slots = emptyArray<Slot>()
    var offHand = emptySlot()
    val properties = mutableMapOf<Int, Int>()
    var ready = false
}
