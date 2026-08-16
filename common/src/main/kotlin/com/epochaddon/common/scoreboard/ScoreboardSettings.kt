package com.epochaddon.common.scoreboard

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import java.text.DecimalFormat
import java.time.DateTimeException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.logging.Logger

enum class ScoreboardOwnershipMode {
    FORCE,
    YIELD;
}

data class ScoreboardProviderSettings(
    val key: String,
    val enabled: Boolean,
    val order: Int,
    val maxLines: Int,
    val separatorBefore: Boolean,
    val permission: String?,
    val worlds: Set<String>,
    val excludedWorlds: Set<String>,
)

private data class ScoreboardProviderDefaults(
    val enabled: Boolean,
    val maxLines: Int,
    val separatorBefore: Boolean,
    val permission: String?,
    val worlds: Set<String>,
    val excludedWorlds: Set<String>,
)

private data class ScoreboardProviderOverride(
    val enabled: Boolean?,
    val order: Int?,
    val maxLines: Int?,
    val separatorBefore: Boolean?,
    val permission: String?,
    val hasPermission: Boolean,
    val worlds: Set<String>?,
    val excludedWorlds: Set<String>?,
)

class ScoreboardSettings private constructor(
    val defaultEnabled: Boolean,
    val persistPlayerPreferences: Boolean,
    val titleTemplate: String,
    val refreshTicks: Long,
    val maxLines: Int,
    val hideWhenEmpty: Boolean,
    val ownershipMode: ScoreboardOwnershipMode,
    val headerLines: List<String>,
    val footerLines: List<String>,
    val separatorEnabled: Boolean,
    val separatorTemplate: String,
    val dateFormatter: DateTimeFormatter,
    val dateZone: ZoneId,
    val balancePattern: String,
    val compactBalance: Boolean,
    val balanceSuffixes: Map<Double, String>,
    val balanceUnavailable: String,
    val affiliationUnavailable: String,
    val externalPlaceholderUnavailable: String,
    val staticPlaceholders: Map<String, String>,
    private val providerDefaults: ScoreboardProviderDefaults,
    private val providerOverrides: Map<String, ScoreboardProviderOverride>,
) {

    fun provider(ownerName: String, id: String, options: ScoreboardProviderOptions): ScoreboardProviderSettings {
        val key = providerKey(ownerName, id)
        val override = providerOverrides[key]
        return ScoreboardProviderSettings(
            key = key,
            enabled = override?.enabled ?: (providerDefaults.enabled && options.enabledByDefault),
            order = override?.order ?: options.order,
            maxLines = (override?.maxLines ?: minOf(providerDefaults.maxLines, options.maxLines))
                .coerceIn(1, maxLines),
            separatorBefore = override?.separatorBefore
                ?: (providerDefaults.separatorBefore && options.separatorBefore),
            permission = if (override?.hasPermission == true) {
                override.permission
            } else {
                options.permission ?: providerDefaults.permission
            },
            worlds = override?.worlds ?: providerDefaults.worlds,
            excludedWorlds = override?.excludedWorlds ?: providerDefaults.excludedWorlds,
        )
    }

    companion object {
        private const val MINECRAFT_MAX_LINES = 15
        private const val DEFAULT_TITLE = "<blue><bold>Epoch</bold></blue><aqua><bold>MC</bold></aqua>"

        fun load(config: FileConfiguration, logger: Logger): ScoreboardSettings {
            val maxLines = config.getInt("scoreboard.max-lines", MINECRAFT_MAX_LINES)
                .coerceIn(1, MINECRAFT_MAX_LINES)
            val ownershipMode = parseOwnershipMode(
                config.getString("scoreboard.ownership-mode", "force"),
                logger,
            )
            val defaultsPath = "scoreboard.provider-defaults"
            val providerDefaults = ScoreboardProviderDefaults(
                enabled = config.getBoolean("$defaultsPath.enabled", true),
                maxLines = config.getInt("$defaultsPath.max-lines", maxLines).coerceIn(1, maxLines),
                separatorBefore = config.getBoolean("$defaultsPath.separator-before", true),
                permission = config.getString("$defaultsPath.permission")?.trim()?.takeIf(String::isNotEmpty),
                worlds = normalizedSet(config.getStringList("$defaultsPath.worlds")),
                excludedWorlds = normalizedSet(config.getStringList("$defaultsPath.excluded-worlds")),
            )

            return ScoreboardSettings(
                defaultEnabled = config.getBoolean(
                    "scoreboard.default-enabled",
                    config.getBoolean("scoreboard.enabled", true),
                ),
                persistPlayerPreferences = config.getBoolean("scoreboard.persist-player-preferences", true),
                titleTemplate = config.getString("scoreboard.title", DEFAULT_TITLE) ?: DEFAULT_TITLE,
                refreshTicks = config.getLong("scoreboard.refresh-ticks", 20L).coerceAtLeast(1L),
                maxLines = maxLines,
                hideWhenEmpty = config.getBoolean("scoreboard.hide-when-empty", false),
                ownershipMode = ownershipMode,
                headerLines = config.getStringList("scoreboard.header-lines"),
                footerLines = config.getStringList("scoreboard.footer-lines"),
                separatorEnabled = config.getBoolean("scoreboard.separator.enabled", true),
                separatorTemplate = config.getString("scoreboard.separator.line", "") ?: "",
                dateFormatter = parseDateFormatter(
                    config.getString("scoreboard.placeholders.date-format", "yyyy/MM/dd"),
                    logger,
                ),
                dateZone = parseZoneId(
                    config.getString("scoreboard.placeholders.time-zone", "Asia/Shanghai"),
                    logger,
                ),
                balancePattern = parseBalancePattern(
                    config.getString("scoreboard.placeholders.balance-format", "#,##0.##"),
                    logger,
                ),
                compactBalance = config.getBoolean("scoreboard.placeholders.balance-compact", true),
                balanceSuffixes = linkedMapOf(
                    1_000_000_000_000.0 to
                        (config.getString("scoreboard.placeholders.balance-suffixes.trillion", "t") ?: "t"),
                    1_000_000_000.0 to
                        (config.getString("scoreboard.placeholders.balance-suffixes.billion", "b") ?: "b"),
                    1_000_000.0 to
                        (config.getString("scoreboard.placeholders.balance-suffixes.million", "m") ?: "m"),
                    1_000.0 to
                        (config.getString("scoreboard.placeholders.balance-suffixes.thousand", "k") ?: "k"),
                ),
                balanceUnavailable = config.getString("scoreboard.placeholders.balance-unavailable", "--") ?: "--",
                affiliationUnavailable = config.getString(
                    "scoreboard.placeholders.affiliation-unavailable",
                    "无",
                ) ?: "无",
                externalPlaceholderUnavailable = config.getString(
                    "scoreboard.placeholders.external-unavailable",
                    "--",
                ) ?: "--",
                staticPlaceholders = loadStaticPlaceholders(config),
                providerDefaults = providerDefaults,
                providerOverrides = loadProviderOverrides(config),
            )
        }

        fun providerKey(ownerName: String, id: String): String =
            "${normalizeKey(ownerName)}.${normalizeKey(id)}"

        private fun loadProviderOverrides(config: FileConfiguration): Map<String, ScoreboardProviderOverride> {
            val root = config.getConfigurationSection("scoreboard.providers") ?: return emptyMap()
            return buildMap {
                for (ownerName in root.getKeys(false)) {
                    val owner = root.getConfigurationSection(ownerName) ?: continue
                    for (providerId in owner.getKeys(false)) {
                        val section = owner.getConfigurationSection(providerId) ?: continue
                        put(providerKey(ownerName, providerId), section.toOverride())
                    }
                }
            }
        }

        private fun loadStaticPlaceholders(config: FileConfiguration): Map<String, String> {
            val section = config.getConfigurationSection("scoreboard.placeholders.static") ?: return emptyMap()
            return section.getKeys(false).associate { key ->
                normalizeKey(key) to (section.getString(key) ?: "")
            }
        }

        private fun ConfigurationSection.toOverride(): ScoreboardProviderOverride = ScoreboardProviderOverride(
            enabled = getBooleanOrNull("enabled"),
            order = getIntOrNull("order"),
            maxLines = getIntOrNull("max-lines")?.coerceAtLeast(1),
            separatorBefore = getBooleanOrNull("separator-before"),
            permission = getString("permission")?.trim()?.takeIf(String::isNotEmpty),
            hasPermission = contains("permission"),
            worlds = getStringSetOrNull("worlds"),
            excludedWorlds = getStringSetOrNull("excluded-worlds"),
        )

        private fun ConfigurationSection.getBooleanOrNull(path: String): Boolean? =
            if (contains(path)) getBoolean(path) else null

        private fun ConfigurationSection.getIntOrNull(path: String): Int? =
            if (contains(path)) getInt(path) else null

        private fun ConfigurationSection.getStringSetOrNull(path: String): Set<String>? =
            if (contains(path)) normalizedSet(getStringList(path)) else null

        private fun normalizedSet(values: List<String>): Set<String> =
            values.map { it.trim().lowercase(Locale.ROOT) }.filter(String::isNotEmpty).toSet()

        private fun normalizeKey(value: String): String = value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_')
            .ifEmpty { "unnamed" }

        private fun parseOwnershipMode(raw: String?, logger: Logger): ScoreboardOwnershipMode {
            return when (raw?.trim()?.lowercase(Locale.ROOT)) {
                null, "", "force" -> ScoreboardOwnershipMode.FORCE
                "yield", "cooperative", "respect-other" -> ScoreboardOwnershipMode.YIELD
                else -> {
                    logger.warning("无效的 scoreboard.ownership-mode=$raw，使用 force")
                    ScoreboardOwnershipMode.FORCE
                }
            }
        }

        private fun parseDateFormatter(raw: String?, logger: Logger): DateTimeFormatter {
            val pattern = raw?.takeIf(String::isNotBlank) ?: "yyyy/MM/dd"
            return try {
                DateTimeFormatter.ofPattern(pattern, Locale.ROOT)
            } catch (exception: IllegalArgumentException) {
                logger.warning("无效的 scoreboard.placeholders.date-format=$pattern，使用 yyyy/MM/dd")
                DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)
            }
        }

        private fun parseZoneId(raw: String?, logger: Logger): ZoneId {
            val zone = raw?.takeIf(String::isNotBlank) ?: "Asia/Shanghai"
            return try {
                ZoneId.of(zone)
            } catch (exception: DateTimeException) {
                logger.warning("无效的 scoreboard.placeholders.time-zone=$zone，使用 Asia/Shanghai")
                ZoneId.of("Asia/Shanghai")
            }
        }

        private fun parseBalancePattern(raw: String?, logger: Logger): String {
            val pattern = raw?.takeIf(String::isNotBlank) ?: "#,##0.##"
            return try {
                DecimalFormat(pattern)
                pattern
            } catch (exception: IllegalArgumentException) {
                logger.warning("无效的 scoreboard.placeholders.balance-format=$pattern，使用 #,##0.##")
                "#,##0.##"
            }
        }
    }
}
