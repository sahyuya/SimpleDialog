package com.github.sahyuya.simpleDialog.tag

import com.github.sahyuya.simpleDialog.SimpleDialog
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Team

class TagManager(private val plugin: SimpleDialog) {

    private var updateTask: BukkitTask? = null

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
        if (playtime > maxPlaytime) {
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
        val tagText = buildTagText(purpose, genres)
        setPlayerTag(player, tagText)
    }

    private fun buildTagText(purpose: String?, genres: List<String>): String {
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

        val genreText = if (genres.isNotEmpty()) {
            val color = plugin.configManager.genreColor
            genres.joinToString(" ") { "$color#$it" }
        } else {
            ""
        }

        var format = plugin.configManager.tagFormat
        format = format.replace("{purpose}", purposeText)
        format = format.replace("{genres}", genreText)

        return ChatColor.translateAlternateColorCodes('&', format)
    }

    private fun setPlayerTag(player: Player, tagText: String) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        var team = scoreboard.getTeam("sd_${player.name}")

        if (team == null) {
            team = scoreboard.registerNewTeam("sd_${player.name}")
        }

        team.suffix(Component.text(tagText))
        team.addEntry(player.name)
    }

    private fun removeTag(player: Player) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val team = scoreboard.getTeam("sd_${player.name}")

        team?.let {
            it.removeEntry(player.name)
            it.unregister()
        }
    }

    fun clearPlayerTag(player: Player) {
        plugin.playerDataManager.clearTags(player.uniqueId)
        removeTag(player)
    }

    private fun checkAndRemoveExpiredTags() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val playtime = plugin.playerDataManager.getPlaytime(player.uniqueId)
            val maxPlaytime = plugin.configManager.maxPlaytime

            if (playtime > maxPlaytime) {
                clearPlayerTag(player)
            }
        }
    }
}