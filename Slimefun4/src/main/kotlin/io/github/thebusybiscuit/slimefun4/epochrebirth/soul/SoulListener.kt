package io.github.thebusybiscuit.slimefun4.epochrebirth.soul

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.RebirthConfig
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.PdcKeys
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItem
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItems
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import kotlin.math.sqrt

class SoulListener(
    private val config: RebirthConfig,
    private val items: RebirthItems,
    private val keys: PdcKeys,
    private val language: LanguageService
) : Listener {

    private val captureRange = 2.5
    private val captureRangeSquared = captureRange * captureRange

    /**
     * 原版灵魂疾行音效事件：内含多个 soul_speed*.ogg，
     * 播放时客户端自动随机选一个。枚举无对应常量，从注册表获取。
     */
    private val soulSpeedSound: Sound =
        Registry.SOUNDS.get(NamespacedKey.minecraft("item.soul_speed")) ?: Sound.PARTICLE_SOUL_ESCAPE

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val type = event.entity.type

        config.boneDrops[type]?.let { range ->
            val count = range.random()
            if (count > 0) {
                event.drops.add(ItemStack(Material.BONE, count))
            }
        }

        if (type !in config.soulAnimals) return
        val location = event.entity.location
        val world = location.world ?: return
        val cloud = world.spawn(location, AreaEffectCloud::class.java) { cloud ->
            cloud.radius = 0.6f
            cloud.waitTime = 0
            cloud.duration = config.soulCloudSeconds * 20
            cloud.durationOnUse = 0
            cloud.particle = Particle.SOUL
            cloud.reapplicationDelay = 0
            cloud.radiusPerTick = 0f
        }
        cloud.persistentDataContainer.set(keys.soulCloud, PersistentDataType.BYTE, 1)
    }

    @EventHandler
    fun onInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        val cloud = event.rightClicked as? AreaEffectCloud ?: return
        val player = event.player
        // 主手或副手持有缚魂瓶均可捕获；副手瓶装魂同样拦截，防止被投掷
        val captured = if (event.hand == EquipmentSlot.OFF_HAND) {
            capture(player, cloud, EquipmentSlot.OFF_HAND)
        } else {
            capture(player, cloud, EquipmentSlot.HAND)
        }
        if (captured) {
            event.isCancelled = true
        } else if (items.identityOf(player.inventory.itemInOffHand) != null) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val player = event.player
        val mainHand = player.inventory.itemInMainHand
        val offHand = player.inventory.itemInOffHand

        when (items.identityOf(mainHand)) {
            RebirthItem.SOUL_BOTTLE -> {
                // 手持缚魂瓶：尝试捕获灵魂；无论是否捕获到都取消事件，防止瓶子被投掷丢失
                captureNearby(player, EquipmentSlot.HAND)
                event.isCancelled = true
            }
            RebirthItem.SOUL -> event.isCancelled = true // 防止瓶装魂被投掷丢失
            else -> {}
        }

        // 副手持缚魂瓶/瓶装魂右键会被原版当经验瓶投掷，必须拦截；
        // 仅当投掷动作来自副手（event.hand == OFF_HAND）时处理，避免影响主手正常交互
        if (event.hand == EquipmentSlot.OFF_HAND) {
            when (items.identityOf(offHand)) {
                RebirthItem.SOUL_BOTTLE -> {
                    captureNearby(player, EquipmentSlot.OFF_HAND)
                    event.isCancelled = true
                }
                RebirthItem.SOUL -> event.isCancelled = true
                else -> {}
            }
        }
    }

    private fun captureNearby(player: Player, hand: EquipmentSlot = EquipmentSlot.HAND) {
        val eye = player.eyeLocation
        val cloud = player.world.getNearbyEntities(eye, captureRange, captureRange, captureRange)
            .filterIsInstance<AreaEffectCloud>()
            .filter { it.persistentDataContainer.has(keys.soulCloud, PersistentDataType.BYTE) }
            .minByOrNull { it.location.distanceSquared(eye) }
            ?: return
        if (cloud.location.distanceSquared(eye) > captureRangeSquared) return
        capture(player, cloud, hand)
    }

    /** 尝试用指定手的缚魂瓶捕获灵魂。成功返回 true。 */
    private fun capture(player: Player, cloud: AreaEffectCloud, hand: EquipmentSlot = EquipmentSlot.HAND): Boolean {
        val inventory = player.inventory
        val handStack = if (hand == EquipmentSlot.HAND) inventory.itemInMainHand else inventory.itemInOffHand
        if (items.identityOf(handStack) != RebirthItem.SOUL_BOTTLE) return false

        cloud.remove()
        val soul = items.create(RebirthItem.SOUL)
        if (handStack.amount <= 1) {
            if (hand == EquipmentSlot.HAND) inventory.setItemInMainHand(soul) else inventory.setItemInOffHand(soul)
        } else {
            handStack.amount -= 1
            inventory.addItem(soul)
                .values
                .forEach { player.world.dropItem(player.location, it) }
        }
        player.playSound(player.location, soulSpeedSound, 1f, 1f)
        player.sendMessage(language.component("messages.soul-captured"))
        return true
    }
}
