package com.epochaddon.skills.service

import com.epochaddon.skills.config.SkillSettings
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class SkillBossBarService(
    private val plugin: JavaPlugin,
) {
    private data class ActiveBar(
        val bar: BossBar,
        val hideTask: BukkitTask,
    )

    private val miniMessage = MiniMessage.miniMessage()
    private val activeBars = mutableMapOf<UUID, ActiveBar>()

    fun show(
        player: Player,
        professionName: String,
        experience: Long,
        gained: Long,
        progress: Float,
        settings: SkillSettings,
    ) {
        hide(player)
        val title = settings.bossBarTitle
            .replace("{profession}", professionName)
            .replace("{experience}", experience.toString())
            .replace("{gained}", gained.toString())
        val bar = BossBar.bossBar(
            text(title),
            progress.coerceIn(0.0f, 1.0f),
            settings.bossBarColor,
            BossBar.Overlay.PROGRESS,
        )
        player.showBossBar(bar)
        val task = plugin.server.scheduler.runTaskLater(
            plugin,
            Runnable { hide(player) },
            settings.bossBarDurationTicks,
        )
        activeBars[player.uniqueId] = ActiveBar(bar, task)
    }

    fun hide(player: Player) {
        val active = activeBars.remove(player.uniqueId) ?: return
        active.hideTask.cancel()
        player.hideBossBar(active.bar)
    }

    fun close() {
        for ((playerId, active) in activeBars.toMap()) {
            active.hideTask.cancel()
            plugin.server.getPlayer(playerId)?.hideBossBar(active.bar)
        }
        activeBars.clear()
    }

    private fun text(raw: String): Component = miniMessage.deserialize("<italic:false>$raw")
}
