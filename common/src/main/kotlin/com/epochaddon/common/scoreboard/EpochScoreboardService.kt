package com.epochaddon.common.scoreboard

import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID
import java.util.logging.Level
import kotlin.math.abs

class EpochScoreboardService(
    private val plugin: JavaPlugin,
    initialSettings: ScoreboardSettings,
) : ScoreboardService, Listener {

    private data class ProviderKey(val owner: Plugin, val id: String)

    private data class RegisteredProvider(
        val key: ProviderKey,
        val options: ScoreboardProviderOptions,
        val provider: ScoreboardProvider,
    )

    private data class ProviderFailure(val key: ProviderKey, val playerId: UUID)

    private data class ScoreboardFrame(
        val title: Component,
        val lines: List<Component>,
    )

    private val miniMessage = MiniMessage.miniMessage()
    private val economy = VaultEconomyBridge(plugin)
    private val nodes = NodesBridge(plugin)
    private val placeholderApi = PlaceholderApiBridge(plugin)
    private val preferenceKey = NamespacedKey(plugin, "scoreboard_enabled")
    private val providers = mutableMapOf<ProviderKey, RegisteredProvider>()
    private val boards = mutableMapOf<UUID, Scoreboard>()
    private val lastFrames = mutableMapOf<UUID, ScoreboardFrame>()
    private val playerStates = mutableMapOf<UUID, Boolean>()
    private val loggedFailures = mutableSetOf<ProviderFailure>()
    private val loggedTruncations = mutableSetOf<String>()
    private val loggedTemplateFailures = mutableSetOf<String>()
    private var settings = initialSettings
    private var refreshTask: BukkitTask? = null
    private var running = false

    fun start() {
        running = true
        plugin.server.pluginManager.registerEvents(this, plugin)
        ensureRefreshTask()
        refreshAll()
    }

    fun stop() {
        running = false
        refreshTask?.cancel()
        refreshTask = null
        clearBoards()
        providers.clear()
        playerStates.clear()
        loggedFailures.clear()
        loggedTruncations.clear()
        loggedTemplateFailures.clear()
    }

    fun updateSettings(updated: ScoreboardSettings) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { updateSettings(updated) })
            return
        }

        settings = updated
        refreshTask?.cancel()
        refreshTask = null
        lastFrames.clear()
        loggedTruncations.clear()
        loggedTemplateFailures.clear()
        ensureRefreshTask()
        refreshAll()
    }

    fun providerStatusLines(): List<String> = providers.values
        .map { registration ->
            val resolved = resolvedSettings(registration)
            val state = if (resolved.enabled) "on" else "off"
            "${resolved.key}: $state, order=${resolved.order}, max-lines=${resolved.maxLines}"
        }
        .sorted()

    override fun registerProvider(
        owner: Plugin,
        id: String,
        options: ScoreboardProviderOptions,
        provider: ScoreboardProvider,
    ) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { registerProvider(owner, id, options, provider) })
            return
        }

        require(id.isNotBlank()) { "Scoreboard provider id cannot be blank" }
        val key = ProviderKey(owner, id)
        providers[key] = RegisteredProvider(key, options, provider)
        loggedFailures.removeAll { it.key == key }
        loggedTruncations.remove(ScoreboardSettings.providerKey(owner.name, id))
        refreshAll()
    }

    override fun unregisterProvider(owner: Plugin, id: String) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { unregisterProvider(owner, id) })
            return
        }

        val key = ProviderKey(owner, id)
        providers.remove(key)
        loggedFailures.removeAll { it.key == key }
        loggedTruncations.remove(ScoreboardSettings.providerKey(owner.name, id))
        refreshAll()
    }

    override fun isEnabled(player: Player): Boolean {
        if (settings.persistPlayerPreferences) {
            val stored = player.persistentDataContainer.get(preferenceKey, PersistentDataType.BYTE)
            if (stored != null) {
                return stored.toInt() != 0
            }
        }
        return playerStates[player.uniqueId] ?: settings.defaultEnabled
    }

    override fun setEnabled(player: Player, enabled: Boolean) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { setEnabled(player, enabled) })
            return
        }

        if (settings.persistPlayerPreferences) {
            playerStates.remove(player.uniqueId)
            if (enabled == settings.defaultEnabled) {
                player.persistentDataContainer.remove(preferenceKey)
            } else {
                player.persistentDataContainer.set(
                    preferenceKey,
                    PersistentDataType.BYTE,
                    if (enabled) 1.toByte() else 0.toByte(),
                )
            }
        } else if (enabled == settings.defaultEnabled) {
            playerStates.remove(player.uniqueId)
        } else {
            playerStates[player.uniqueId] = enabled
        }

        if (running) {
            refresh(player)
        }
    }

    override fun refresh(player: Player) {
        if (!running || !player.isOnline) {
            return
        }

        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { refresh(player) })
            return
        }

        if (isEnabled(player)) {
            render(player)
        } else {
            clearBoard(player)
        }
    }

    override fun refreshAll() {
        if (!running) {
            return
        }

        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable(::refreshAll))
            return
        }

        Bukkit.getOnlinePlayers().forEach(::refresh)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.server.scheduler.runTask(plugin, Runnable { refresh(event.player) })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        boards.remove(event.player.uniqueId)
        lastFrames.remove(event.player.uniqueId)
        loggedFailures.removeAll { it.playerId == event.player.uniqueId }
        if (!settings.persistPlayerPreferences) {
            playerStates.remove(event.player.uniqueId)
        }
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        val removedKeys = providers.keys.filter { it.owner === event.plugin }.toSet()
        if (removedKeys.isEmpty()) {
            return
        }

        providers.keys.removeAll(removedKeys)
        loggedFailures.removeAll { it.key in removedKeys }
        removedKeys.forEach { loggedTruncations.remove(ScoreboardSettings.providerKey(it.owner.name, it.id)) }
        refreshAll()
    }

    private fun render(player: Player) {
        val existingBoard = boards[player.uniqueId]
        val mainScoreboard = Bukkit.getScoreboardManager().mainScoreboard
        if (
            player.scoreboard !== existingBoard &&
            player.scoreboard !== mainScoreboard &&
            settings.ownershipMode == ScoreboardOwnershipMode.YIELD
        ) {
            return
        }

        val frame = buildFrame(player)
        if (settings.hideWhenEmpty && frame.lines.isEmpty()) {
            clearBoard(player)
            return
        }

        val board = existingBoard ?: createBoard().also { boards[player.uniqueId] = it }
        if (player.scoreboard !== board) {
            if (
                settings.ownershipMode == ScoreboardOwnershipMode.YIELD &&
                player.scoreboard !== mainScoreboard
            ) {
                return
            }
            player.scoreboard = board
            lastFrames.remove(player.uniqueId)
        }

        val objective = board.getObjective(OBJECTIVE_NAME)
            ?: board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, frame.title)
        objective.displaySlot = DisplaySlot.SIDEBAR
        objective.numberFormat(NumberFormat.blank())

        if (lastFrames[player.uniqueId] == frame) {
            return
        }

        objective.displayName(frame.title)
        for (index in 0 until MINECRAFT_MAX_LINES) {
            val entry = ENTRY_KEYS[index]
            val team = board.getTeam(teamName(index)) ?: board.registerNewTeam(teamName(index)).also {
                it.addEntry(entry)
            }

            if (index < frame.lines.size) {
                team.prefix(frame.lines[index])
                team.suffix(Component.empty())
                objective.getScore(entry).score = MINECRAFT_MAX_LINES - index
            } else {
                team.prefix(Component.empty())
                team.suffix(Component.empty())
                board.resetScores(entry)
            }
        }
        lastFrames[player.uniqueId] = frame
    }

    private fun buildFrame(player: Player): ScoreboardFrame {
        val sections = providers.values.mapNotNull { registration ->
            val resolved = resolvedSettings(registration)
            if (!isVisible(resolved, player)) {
                return@mapNotNull null
            }
            ScoreboardSectionContent(
                key = resolved.key,
                order = resolved.order,
                maxLines = resolved.maxLines,
                separatorBefore = resolved.separatorBefore,
                lines = providerLines(registration, player),
            )
        }
        val layout = ScoreboardLayout.compose(
            maxLines = settings.maxLines,
            header = settings.headerLines.mapIndexed { index, template ->
                renderTemplate(template, player, "header[$index]")
            },
            footer = settings.footerLines.mapIndexed { index, template ->
                renderTemplate(template, player, "footer[$index]")
            },
            separatorEnabled = settings.separatorEnabled,
            separator = renderTemplate(settings.separatorTemplate, player, "separator"),
            sections = sections,
        )
        logTruncations(layout)
        return ScoreboardFrame(
            title = renderTemplate(settings.titleTemplate, player, "title"),
            lines = layout.lines,
        )
    }

    private fun isVisible(provider: ScoreboardProviderSettings, player: Player): Boolean {
        if (!provider.enabled) {
            return false
        }
        if (provider.permission != null && !player.hasPermission(provider.permission)) {
            return false
        }

        val worldName = player.world.name.lowercase(Locale.ROOT)
        if (provider.worlds.isNotEmpty() && worldName !in provider.worlds) {
            return false
        }
        return worldName !in provider.excludedWorlds
    }

    private fun resolvedSettings(registration: RegisteredProvider): ScoreboardProviderSettings =
        settings.provider(registration.key.owner.name, registration.key.id, registration.options)

    private fun renderTemplate(template: String, player: Player, source: String): Component {
        var rendered = settings.staticPlaceholders.entries.fold(template) { text, (key, value) ->
            text.replace("{$key}", value)
        }
        val balance = economy.balance(player)
        val membership = nodes.membership(player)
        val values = mapOf(
            "player" to player.name,
            "world" to player.world.name,
            "online" to Bukkit.getOnlinePlayers().size.toString(),
            "max_players" to Bukkit.getMaxPlayers().toString(),
            "date" to settings.dateFormatter.format(ZonedDateTime.now(settings.dateZone)),
            "balance" to (balance?.let { formatBalance(it.amount) } ?: settings.balanceUnavailable),
            "balance_raw" to (balance?.let { formatRawBalance(it.amount) } ?: settings.balanceUnavailable),
            "balance_vault" to balance?.formatted.orEmpty().ifEmpty { settings.balanceUnavailable },
            "country" to (membership?.nation ?: settings.affiliationUnavailable),
            "nation" to (membership?.nation ?: settings.affiliationUnavailable),
            "town" to (membership?.town ?: settings.affiliationUnavailable),
        )
        rendered = values.entries.fold(rendered) { text, (key, value) ->
            text.replace("{$key}", miniMessage.escapeTags(value))
        }
        rendered = PLACEHOLDER_API_PATTERN.replace(rendered) { match ->
            val value = placeholderApi.resolve(player, match.value) ?: settings.externalPlaceholderUnavailable
            miniMessage.escapeTags(value)
        }
        return try {
            miniMessage.deserialize(rendered)
        } catch (exception: Exception) {
            if (loggedTemplateFailures.add(source)) {
                plugin.logger.log(Level.WARNING, "计分板配置 $source 的 MiniMessage 格式无效", exception)
            }
            Component.text(template)
        }
    }

    private fun formatBalance(balance: Double): String {
        if (!settings.compactBalance) {
            return formatRawBalance(balance)
        }
        val scale = settings.balanceSuffixes.entries.firstOrNull { abs(balance) >= it.key }
            ?: return formatRawBalance(balance)
        return "${formatRawBalance(balance / scale.key)}${scale.value}"
    }

    private fun formatRawBalance(balance: Double): String = DecimalFormat(
        settings.balancePattern,
        DecimalFormatSymbols.getInstance(Locale.ROOT),
    ).format(balance)

    private fun logTruncations(layout: ScoreboardLayoutResult) {
        for ((key, dropped) in layout.truncatedSections) {
            if (loggedTruncations.add(key)) {
                plugin.logger.warning("计分板提供器 $key 有 $dropped 行因行数预算被截断")
            }
        }
        if (layout.headerTruncated > 0 && loggedTruncations.add("@header")) {
            plugin.logger.warning("计分板页眉有 ${layout.headerTruncated} 行因全局行数限制被截断")
        }
        if (layout.footerTruncated > 0 && loggedTruncations.add("@footer")) {
            plugin.logger.warning("计分板页脚有 ${layout.footerTruncated} 行因全局行数限制被截断")
        }
    }

    private fun providerLines(registration: RegisteredProvider, player: Player): List<Component> {
        val failure = ProviderFailure(registration.key, player.uniqueId)
        return try {
            registration.provider.lines(player).also { loggedFailures.remove(failure) }
        } catch (exception: Exception) {
            if (loggedFailures.add(failure)) {
                plugin.logger.log(
                    Level.WARNING,
                    "计分板提供器 ${registration.key.owner.name}:${registration.key.id} 为玩家 ${player.name} 生成内容失败",
                    exception,
                )
            }
            emptyList()
        }
    }

    private fun maintainBoards() {
        if (running) {
            Bukkit.getOnlinePlayers().forEach(::refresh)
        }
    }

    private fun ensureRefreshTask() {
        if (!running || refreshTask != null) {
            return
        }
        refreshTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable(::maintainBoards),
            1L,
            settings.refreshTicks,
        )
    }

    private fun clearBoards() {
        val mainScoreboard = Bukkit.getScoreboardManager().mainScoreboard
        for ((playerId, board) in boards) {
            val player = Bukkit.getPlayer(playerId) ?: continue
            if (player.scoreboard === board) {
                player.scoreboard = mainScoreboard
            }
        }
        boards.clear()
        lastFrames.clear()
    }

    private fun clearBoard(player: Player) {
        val board = boards.remove(player.uniqueId) ?: return
        lastFrames.remove(player.uniqueId)
        loggedFailures.removeAll { it.playerId == player.uniqueId }
        if (player.scoreboard === board) {
            player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        }
    }

    private fun createBoard(): Scoreboard = Bukkit.getScoreboardManager().newScoreboard

    private fun teamName(index: Int): String = "epoch_line_$index"

    companion object {
        private const val OBJECTIVE_NAME = "epoch_sidebar"
        private const val MINECRAFT_MAX_LINES = 15
        private val PLACEHOLDER_API_PATTERN = Regex("%[^%\\s]+%")
        private val ENTRY_KEYS = (0 until MINECRAFT_MAX_LINES).map { "\u00a7${it.toString(16)}" }
    }
}
