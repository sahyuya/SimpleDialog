package com.github.sahyuya.simpleDialog.tag

import com.github.sahyuya.simpleDialog.SimpleDialog
import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective

class TagManager(private val plugin: SimpleDialog) {

    private var updateTask: BukkitTask? = null
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    companion object {
        private const val OBJECTIVE_NAME = "sd_tags"
    }

    fun startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updateAllTags()
            checkAndRemoveExpiredTags()
        }, 0L, 20L * 60L) // Update every minute
    }

    fun stopUpdateTask() {
        updateTask?.cancel()
        updateTask = null
    }

    fun updateAllTags() {
        Bukkit.getOnlinePlayers().forEach { player ->
            updateTag(player)
        }
    }

    fun updateTag(player: Player) {
        val playtime = plugin.playerDataManager.getPlaytime(player.uniqueId)
        val maxPlaytime = plugin.configManager.maxPlaytime

        // Check if player should have tag
        if (playtime != null && playtime > maxPlaytime) {
            removeTag(player)
            return
        }

        val data = plugin.playerDataManager.getPlayerData(player.uniqueId)
        val purpose = data.purpose
        val genres = data.genres

        // If no purpose or genres, remove tag
        if (purpose == null && genres.isEmpty()) {
            removeTag(player)
            return
        }

        // Build tag
        val tagComponent = buildTagComponent(purpose, genres)
        setPlayerTag(player, tagComponent)
    }

    private fun buildTagComponent(purpose: String?, genres: List<String>): Component {
        var result = Component.empty()
        var hasContent = false

        // Add purpose
        if (purpose != null) {
            val purposeText = when (purpose) {
                "building" -> {
                    val color = plugin.configManager.buildingColor
                    "${color}建築目的"
                }
                "sightseeing" -> {
                    val color = plugin.configManager.sightseeingColor
                    "${color}観光目的"
                }
                else -> ""
            }

            if (purposeText.isNotEmpty()) {
                result = result.append(legacySerializer.deserialize(purposeText))
                hasContent = true
            }
        }

        // Add genres
        if (genres.isNotEmpty()) {
            val genreColor = plugin.configManager.genreColor
            genres.forEach { genre ->
                if (hasContent) {
                    result = result.append(Component.text(" "))
                }
                result = result.append(legacySerializer.deserialize("${genreColor}#${genre}"))
                hasContent = true
            }
        }

        return result
    }

    private fun setPlayerTag(player: Player, tagComponent: Component) {
        val objective = ensureObjective() ?: return
        val score = objective.getScore(player)
        score.score = 0
        score.numberFormat(NumberFormat.fixed(tagComponent))
    }

    private fun removeTag(player: Player) {
        val objective = ensureObjective() ?: return
        val score = objective.getScore(player)
        if (score.isScoreSet) {
            score.resetScore()
        }
    }

    private fun ensureObjective(): Objective? {
        val manager = Bukkit.getScoreboardManager() ?: return null
        val scoreboard = manager.mainScoreboard

        // Get or create objective
        var objective = scoreboard.getObjective(OBJECTIVE_NAME)
        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                OBJECTIVE_NAME,
                Criteria.DUMMY,
                Component.empty()
            )
        }

        // Set display slot to BELOW_NAME
        if (objective.displaySlot != DisplaySlot.BELOW_NAME) {
            val existing = scoreboard.getObjective(DisplaySlot.BELOW_NAME)
            if (existing != null && existing.name != OBJECTIVE_NAME) {
                plugin.logger.warning("SimpleDialog is overriding existing below-name display: ${existing.name}")
            }
            objective.displaySlot = DisplaySlot.BELOW_NAME
        }

        return objective
    }

    fun clearPlayerTag(player: Player) {
        plugin.playerDataManager.clearTags(player.uniqueId)
        removeTag(player)
    }

    private fun checkAndRemoveExpiredTags() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val playtime = plugin.playerDataManager.getPlaytime(player.uniqueId)
            val maxPlaytime = plugin.configManager.maxPlaytime

            if (playtime != null && playtime > maxPlaytime) {
                clearPlayerTag(player)
            }
        }
    }
}