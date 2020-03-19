package com.github.gjum.minecraft.botlin.api

import com.github.steveice10.mc.auth.data.GameProfile
import java.util.UUID

interface Avatar {
	val profile: GameProfile

	val playerEntity: PlayerEntity?
	val world: World?
	val playerList: Map<UUID, PlayerListItem>
	val window: Window?
	val hotbarSelection: Int?

	val mainHandSlot: Slot? get() = window?.hotbar?.get(hotbarSelection ?: 0)

	/**
	 * Indicates if the account has received all its state yet,
	 * such as position/health/food/exp/chunks.
	 */
	val ingame: Boolean
		get() = (playerEntity?.position != null
			&& playerEntity?.health != null
			&& playerEntity?.experience != null
			&& world != null)

	/**
	 * Indicates if the account is alive and [ingame].
	 */
	val alive get() = (playerEntity?.health ?: 0.0f) > 0.0f && ingame
}
