package com.epochaddon.minerals.service

import org.bukkit.Server
import org.bukkit.entity.Player
import java.lang.reflect.Method
import java.util.logging.Logger

/**
 * Optional bridge to EpochSkills. Keeping its API behind reflection lets EpochMinerals run
 * independently while still using skill unlocks and experience when EpochSkills is installed.
 */
interface EpochSkillsBridge {
    fun hasUnlock(player: Player, unlockId: String): Boolean

    fun addExperience(player: Player, professionId: String, amount: Long)

    fun sourceExperience(professionId: String, sourceId: String): Long
}

object EpochSkillsIds {
    const val DIGGING_PROFESSION = "gathering.digging"
    const val DIGGING_RESOURCE_SYSTEM = "epochminerals:resource_system"
    const val DIGGING_NEXT_DROP_DISPLAY = "epochminerals:next_drop_display"
}

object EpochSkillsBridgeLoader {
    private const val PLUGIN_NAME = "EpochSkills"
    private const val SERVICE_CLASS_NAME = "com.epochaddon.skills.api.EpochSkillsService"

    fun load(server: Server, logger: Logger): EpochSkillsBridge? {
        val plugin = server.pluginManager.getPlugin(PLUGIN_NAME) ?: return null

        return try {
            @Suppress("UNCHECKED_CAST")
            val serviceClass = Class.forName(
                SERVICE_CLASS_NAME,
                false,
                plugin.javaClass.classLoader,
            ) as Class<Any>
            val service = server.servicesManager.load(serviceClass) ?: run {
                logger.warning("EpochSkills 已加载，但未注册技能服务；技能树联动已关闭")
                return null
            }
            ReflectiveEpochSkillsBridge(service, serviceClass)
        } catch (exception: ReflectiveOperationException) {
            logger.warning("无法连接 EpochSkills 服务；技能树联动已关闭：${exception.message}")
            null
        } catch (error: LinkageError) {
            logger.warning("EpochSkills API 不兼容；技能树联动已关闭：${error.message}")
            null
        }
    }
}

private class ReflectiveEpochSkillsBridge(
    private val service: Any,
    serviceClass: Class<*>,
) : EpochSkillsBridge {
    private val hasUnlock: Method = serviceClass.getMethod("hasUnlock", Player::class.java, String::class.java)
    private val addExperience: Method = serviceClass.getMethod(
        "addExperience",
        Player::class.java,
        String::class.java,
        Long::class.javaPrimitiveType,
    )
    private val sourceExperience: Method = serviceClass.getMethod(
        "sourceExperience",
        String::class.java,
        String::class.java,
    )

    override fun hasUnlock(player: Player, unlockId: String): Boolean =
        hasUnlock.invoke(service, player, unlockId) as Boolean

    override fun addExperience(player: Player, professionId: String, amount: Long) {
        addExperience.invoke(service, player, professionId, amount)
    }

    override fun sourceExperience(professionId: String, sourceId: String): Long =
        sourceExperience.invoke(service, professionId, sourceId) as Long
}
