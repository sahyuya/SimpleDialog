package com.github.sahyuya.simpleDialog

import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import java.io.File
import java.util.UUID

// タグの保存とネームタグ下の表示を管理する。
class TagManager(
    private val plugin: JavaPlugin,
    private val playtimeTracker: PlaytimeTracker
) {
    data class TagEntry(val tags: List<String>, val expiresAt: Long)

    private val dataFile = File(plugin.dataFolder, "tags.yml")
    private val tagEntries = mutableMapOf<UUID, TagEntry>()

    private var tagDurationMinutes = 60L
    private var tagSeparator = " "

    fun load() {
        reloadSettings()
        tagEntries.clear()
        if (!dataFile.exists()) {
            return
        }
        val config = YamlConfiguration.loadConfiguration(dataFile)
        val section = config.getConfigurationSection("tags") ?: return
        val now = System.currentTimeMillis()
        var changed = false
        for (key in section.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            if (!playtimeTracker.isTracked(uuid)) {
                changed = true
                continue
            }
            val tags = section.getStringList("$key.tags")
            val expiresAt = section.getLong("$key.expires-at")
            if (tags.isEmpty() || expiresAt <= now) {
                changed = true
                continue
            }
            tagEntries[uuid] = TagEntry(tags, expiresAt)
        }
        if (changed) {
            save()
        }
    }

    fun reloadSettings() {
        tagDurationMinutes = plugin.config.getLong("tag-duration-minutes", 60)
        tagSeparator = plugin.config.getString("tag-separator", " ") ?: " "
    }

    fun applyTagsToOnlinePlayers() {
        for (player in Bukkit.getOnlinePlayers()) {
            applyTagsIfActive(player)
        }
    }

    fun applyTagsIfActive(player: Player) {
        val entry = tagEntries[player.uniqueId] ?: return
        if (entry.expiresAt <= System.currentTimeMillis() || !isWithinPlaytime(player)) {
            clearTags(player.uniqueId)
            return
        }
        applyTagsToScoreboard(player, entry.tags)
    }

    fun setTags(player: Player, tags: List<String>) {
        if (tags.isEmpty() || !isWithinPlaytime(player)) {
            clearTags(player.uniqueId)
            return
        }
        val durationMs = tagDurationMinutes.coerceAtLeast(0) * 60_000L
        val expiresAt = System.currentTimeMillis() + durationMs
        tagEntries[player.uniqueId] = TagEntry(tags, expiresAt)
        save()
        applyTagsToScoreboard(player, tags)
    }

    fun clearTags(uuid: UUID) {
        if (tagEntries.remove(uuid) == null) {
            return
        }
        clearTagsFromScoreboard(uuid)
        save()
    }

    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val toRemove = mutableSetOf<UUID>()
        for ((uuid, entry) in tagEntries) {
            if (entry.expiresAt <= now) {
                toRemove.add(uuid)
            }
            if (!playtimeTracker.isTracked(uuid)) {
                toRemove.add(uuid)
            }
        }
        for (player in Bukkit.getOnlinePlayers()) {
            val entry = tagEntries[player.uniqueId] ?: continue
            if (!isWithinPlaytime(player)) {
                toRemove.add(player.uniqueId)
            } else {
                applyTagsToScoreboard(player, entry.tags)
            }
        }
        if (toRemove.isEmpty()) {
            return
        }
        for (uuid in toRemove) {
            tagEntries.remove(uuid)
            clearTagsFromScoreboard(uuid)
        }
        save()
    }

    private fun save() {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
        val config = YamlConfiguration()
        for ((uuid, entry) in tagEntries) {
            val base = "tags.$uuid"
            config.set("$base.tags", entry.tags)
            config.set("$base.expires-at", entry.expiresAt)
        }
        runCatching { config.save(dataFile) }
            .onFailure { plugin.logger.warning("tags.ymlの保存に失敗しました: ${it.message}") }
    }

    private fun applyTagsToScoreboard(player: Player, tags: List<String>) {
        val objective = ensureObjective() ?: return
        val score = objective.getScore(player)
        score.score = 0
        score.numberFormat(NumberFormat.fixed(buildTagComponent(tags)))
    }

    private fun clearTagsFromScoreboard(uuid: UUID) {
        val objective = ensureObjective() ?: return
        val score = objective.getScore(Bukkit.getOfflinePlayer(uuid))
        if (score.isScoreSet) {
            score.resetScore()
        }
    }

    private fun ensureObjective(): Objective? {
        val manager = Bukkit.getScoreboardManager() ?: return null
        val scoreboard = manager.mainScoreboard
        // ネームタグ下の目的は1つに統一する。
        var objective = scoreboard.getObjective(OBJECTIVE_NAME)
        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.text(""))
        }
        if (objective.displaySlot != DisplaySlot.BELOW_NAME) {
            val existing = scoreboard.getObjective(DisplaySlot.BELOW_NAME)
            if (existing != null && existing.name != OBJECTIVE_NAME) {
                plugin.logger.warning("SimpleDialogがネームタグ下の表示を上書きします: ${existing.name}")
            }
            objective.displaySlot = DisplaySlot.BELOW_NAME
        }
        return objective
    }

    fun isWithinPlaytime(player: Player): Boolean {
        return playtimeTracker.isWithinLimit(player)
    }

    private fun buildTagComponent(tags: List<String>): Component {
        if (tags.isEmpty()) {
            return Component.empty()
        }
        val separator = TextFormat.component(tagSeparator)
        var result = Component.empty()
        tags.forEachIndexed { index, raw ->
            if (index > 0) {
                result = result.append(separator)
            }
            result = result.append(TextFormat.component(raw))
        }
        return result
    }

    companion object {
        private const val OBJECTIVE_NAME = "sd_tags"
    }
}
