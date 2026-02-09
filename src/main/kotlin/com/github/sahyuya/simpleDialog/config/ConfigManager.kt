package com.github.sahyuya.simpleDialog.config

import com.github.sahyuya.simpleDialog.SimpleDialog
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ConfigManager(private val plugin: SimpleDialog) {

    var showOnFirstJoin: Boolean = true
        private set

    var maxPlaytime: Long = 10800L
        private set

    var tagFormat: String = "&7[&e{purpose}&7] {genres}"
        private set

    var buildingColor: String = "&a"
        private set

    var sightseeingColor: String = "&b"
        private set

    var genreColor: String = "&6"
        private set

    var cleanupOnTagRemoval: Boolean = true
        private set

    lateinit var dialogsJa: FileConfiguration
        private set

    lateinit var dialogsEn: FileConfiguration
        private set

    lateinit var formsJa: FileConfiguration
        private set

    lateinit var formsEn: FileConfiguration
        private set

    fun loadConfig() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()

        val config = plugin.config
        showOnFirstJoin = config.getBoolean("show-on-first-join", true)
        maxPlaytime = config.getLong("tag.max-playtime", 180L) // minutes
        tagFormat = config.getString("tag.format") ?: "&7[&e{purpose}&7] {genres}"
        buildingColor = config.getString("tag.purpose-colors.building") ?: "&a"
        sightseeingColor = config.getString("tag.purpose-colors.sightseeing") ?: "&b"
        genreColor = config.getString("tag.genre-color") ?: "&6"
        cleanupOnTagRemoval = config.getBoolean("cleanup-on-tag-removal", true)
    }

    fun loadDialogs() {
        dialogsJa = loadResourceFile("dialogs_ja.yml")
        dialogsEn = loadResourceFile("dialogs_en.yml")
    }

    fun loadForms() {
        formsJa = loadResourceFile("forms_ja.yml")
        formsEn = loadResourceFile("forms_en.yml")
    }

    private fun loadResourceFile(fileName: String): FileConfiguration {
        val file = File(plugin.dataFolder, fileName)
        if (!file.exists()) {
            plugin.saveResource(fileName, false)
        }
        return YamlConfiguration.loadConfiguration(file)
    }

    fun regenerateFiles() {
        // Delete existing files
        val configFile = File(plugin.dataFolder, "config.yml")
        val dialogsJaFile = File(plugin.dataFolder, "dialogs_ja.yml")
        val dialogsEnFile = File(plugin.dataFolder, "dialogs_en.yml")
        val formsJaFile = File(plugin.dataFolder, "forms_ja.yml")
        val formsEnFile = File(plugin.dataFolder, "forms_en.yml")

        configFile.delete()
        dialogsJaFile.delete()
        dialogsEnFile.delete()
        formsJaFile.delete()
        formsEnFile.delete()

        // Recreate files
        plugin.saveResource("config.yml", true)
        plugin.saveResource("dialogs_ja.yml", true)
        plugin.saveResource("dialogs_en.yml", true)
        plugin.saveResource("forms_ja.yml", true)
        plugin.saveResource("forms_en.yml", true)

        // Reload
        loadConfig()
        loadDialogs()
        loadForms()
    }
}