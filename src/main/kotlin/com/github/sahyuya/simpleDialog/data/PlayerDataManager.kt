package com.github.sahyuya.simpleDialog.data

import com.github.sahyuya.simpleDialog.SimpleDialog
import com.google.gson.JsonParser
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DynamicProfileReader(private val plugin: SimpleDialog) {

    private val dynamicProfileDir = File(plugin.dataFolder.parentFile, "DynamicProfile/UserStatsJSON")

    /**
     * Get player's total playtime in minutes from DynamicProfile
     * Returns null if player data doesn't exist
     */
    fun getPlaytime(uuid: UUID): Long? {
        val playerFile = File(dynamicProfileDir, uuid.toString())
        if (!playerFile.exists() || !playerFile.isFile) {
            return null
        }

        return try {
            val jsonContent = playerFile.readText()
            val jsonObject = JsonParser.parseString(jsonContent).asJsonObject

            // playTime is stored in minutes in DynamicProfile
            jsonObject.get("playTime")?.asLong
        } catch (e: Exception) {
            plugin.logger.warning("Failed to read DynamicProfile data for $uuid: ${e.message}")
            null
        }
    }

    /**
     * Check if player is new (playTime = 0 in DynamicProfile)
     */
    fun isNewPlayer(uuid: UUID): Boolean {
        val playtime = getPlaytime(uuid)
        return playtime == null || playtime == 0L
    }

    /**
     * Check if player has exceeded max playtime
     */
    fun hasExceededMaxPlaytime(uuid: UUID, maxPlaytimeMinutes: Long): Boolean {
        val playtime = getPlaytime(uuid) ?: return false
        return playtime > maxPlaytimeMinutes
    }
}

class PlayerDataManager(private val plugin: SimpleDialog) {

    private val dataFile = File(plugin.dataFolder, "playerdata.yml")
    private lateinit var data: FileConfiguration

    private val playerData = ConcurrentHashMap<UUID, PlayerData>()

    val dynamicProfileReader = DynamicProfileReader(plugin)

    fun loadData() {
        if (!dataFile.exists()) {
            dataFile.parentFile.mkdirs()
            dataFile.createNewFile()
        }

        data = YamlConfiguration.loadConfiguration(dataFile)

        // Load player data
        playerData.clear()
        for (key in data.getKeys(false)) {
            val uuid = UUID.fromString(key)
            val section = data.getConfigurationSection(key) ?: continue

            val purpose = section.getString("purpose")
            val genres = section.getStringList("genres")

            playerData[uuid] = PlayerData(purpose, genres.toMutableList())
        }

        // Clean up expired players
        cleanupExpiredPlayers()
    }

    fun saveData() {
        data = YamlConfiguration()

        for ((uuid, playerData) in playerData) {
            val section = data.createSection(uuid.toString())
            playerData.purpose?.let { section.set("purpose", it) }
            section.set("genres", playerData.genres)
        }

        data.save(dataFile)
    }

    fun getPlayerData(uuid: UUID): PlayerData {
        return playerData.computeIfAbsent(uuid) {
            PlayerData(null, mutableListOf())
        }
    }

    fun removePlayerData(uuid: UUID) {
        playerData.remove(uuid)
    }

    fun hasPlayerData(uuid: UUID): Boolean {
        return playerData.containsKey(uuid)
    }

    fun setPurpose(uuid: UUID, purpose: String) {
        val data = getPlayerData(uuid)
        data.purpose = purpose
    }

    fun setGenres(uuid: UUID, genres: List<String>) {
        val data = getPlayerData(uuid)
        data.genres.clear()
        data.genres.addAll(genres)
    }

    fun getGenres(uuid: UUID): List<String>? {
        return playerData[uuid]?.genres
    }

    fun clearTags(uuid: UUID) {
        val data = playerData[uuid] ?: return
        data.purpose = null
        data.genres.clear()

        if (plugin.configManager.cleanupOnTagRemoval) {
            removePlayerData(uuid)
        }
    }

    fun getPlaytime(uuid: UUID): Long {
        return dynamicProfileReader.getPlaytime(uuid) ?: 0L
    }

    fun isNewPlayer(uuid: UUID): Boolean {
        // New player if: DynamicProfile playTime = 0 AND not in playerdata list
        return dynamicProfileReader.isNewPlayer(uuid) && !hasPlayerData(uuid)
    }

    fun shouldShowDialog(uuid: UUID): Boolean {
        // Show if new player OR (has data and within max playtime)
        if (isNewPlayer(uuid)) {
            return true
        }

        if (!hasPlayerData(uuid)) {
            return false
        }

        val playtime = getPlaytime(uuid)
        val maxPlaytime = plugin.configManager.maxPlaytime
        return playtime <= maxPlaytime
    }

    /**
     * Clean up players who have exceeded max playtime
     */
    fun cleanupExpiredPlayers() {
        val maxPlaytime = plugin.configManager.maxPlaytime
        val toRemove = mutableListOf<UUID>()

        for (uuid in playerData.keys) {
            if (dynamicProfileReader.hasExceededMaxPlaytime(uuid, maxPlaytime)) {
                toRemove.add(uuid)
            }
        }

        toRemove.forEach { uuid ->
            removePlayerData(uuid)
            plugin.logger.info("Removed expired player data for $uuid")
        }

        if (toRemove.isNotEmpty()) {
            saveData()
        }
    }
}

data class PlayerData(
    var purpose: String?,
    val genres: MutableList<String>
)