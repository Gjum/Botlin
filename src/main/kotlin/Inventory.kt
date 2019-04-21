package com.github.gjum.minecraft.botlin

class McWindow( // TODO
    val windowId: Int,
    val inventoryType: String,
    val windowTitle: String,
    val slotCount: Int,
    val entityId: Int?
) {
    var slots = emptyArray<Slot>()
    val properties = mutableMapOf<Int, Int>()
}
