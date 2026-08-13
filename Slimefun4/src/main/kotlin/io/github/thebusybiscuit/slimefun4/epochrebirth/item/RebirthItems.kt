package io.github.thebusybiscuit.slimefun4.epochrebirth.item

import com.destroystokyo.paper.profile.ProfileProperty
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class RebirthItems(private val keys: PdcKeys, private val language: LanguageService) {

    fun create(item: RebirthItem, amount: Int = 1): ItemStack {
        val stack = ItemStack(material(item), amount)
        val meta = stack.itemMeta
        meta.displayName(language.component("items.${item.id}.name"))
        meta.lore(language.components("items.${item.id}.lore").map { it.decoration(TextDecoration.ITALIC, false) })
        meta.persistentDataContainer.set(keys.itemId, PersistentDataType.STRING, item.id)
        if (item == RebirthItem.MEAT && meta is SkullMeta) {
            val profile = Bukkit.createProfile(MEAT_SKULL_UUID, null)
            profile.setProperty(ProfileProperty("textures", MEAT_SKULL_TEXTURE))
            meta.playerProfile = profile
        }
        stack.itemMeta = meta
        return stack
    }

    fun identityOf(stack: ItemStack?): RebirthItem? {
        if (stack == null || stack.type.isAir || !stack.hasItemMeta()) return null
        val id = stack.itemMeta.persistentDataContainer.get(keys.itemId, PersistentDataType.STRING) ?: return null
        return RebirthItem.fromId(id)
    }

    private fun material(item: RebirthItem): Material = when (item) {
        RebirthItem.SOUL_BOTTLE -> Material.EXPERIENCE_BOTTLE
        RebirthItem.SOUL -> Material.DRAGON_BREATH
        RebirthItem.MEAT -> Material.PLAYER_HEAD
        RebirthItem.CORE_BASIC -> Material.NETHERRACK
        RebirthItem.CORE_ADVANCED -> Material.NETHER_WART_BLOCK
        RebirthItem.CORE_ULTIMATE -> Material.RED_NETHER_BRICKS
        RebirthItem.TOTEM_BASIC, RebirthItem.TOTEM_ADVANCED, RebirthItem.TOTEM_ULTIMATE -> Material.TOTEM_OF_UNDYING
    }

    private companion object {
        /** 肉块头颅皮肤：Head Database "Red Meat"（Custom Head ID: 7408） */
        private const val MEAT_SKULL_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzhjZmQwNTg4YThhOTNiOGNkMjFiZGQyY2UxNjU0ODljYjM5Mzk0ODcxNGZkZDg1ZmIxMGU0NGQ0ODg2ZjYifX19"
        private val MEAT_SKULL_UUID: UUID = UUID.nameUUIDFromBytes("epoch-rebirth-meat".toByteArray())
    }
}
