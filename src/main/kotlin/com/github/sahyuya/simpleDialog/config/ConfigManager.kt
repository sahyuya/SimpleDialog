package com.github.sahyuya.simpleDialog.config

import com.github.sahyuya.simpleDialog.SimpleDialog
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

// 設定ファイルの読み込みと管理を行うクラス
class ConfigManager(private val plugin: SimpleDialog) {

    // 初回参加時にDialogを表示するかどうか
    var showOnFirstJoin: Boolean = true
        private set

    // タグを表示する最大プレイ時間（分）
    var maxPlaytime: Long = 180L
        private set

    // 目的タグの色（建築）
    var buildingColor: String = "&a"
        private set

    // 目的タグの色（観光）
    var sightseeingColor: String = "&b"
        private set

    // ジャンルタグの色
    var genreColor: String = "&6"
        private set

    // Dialog設定ファイル（日本語）
    lateinit var dialogsJa: FileConfiguration
        private set

    // Dialog設定ファイル（英語）
    lateinit var dialogsEn: FileConfiguration
        private set

    // Form設定ファイル（日本語・Bedrock向け）
    lateinit var formsJa: FileConfiguration
        private set

    // Form設定ファイル（英語・Bedrock向け）
    lateinit var formsEn: FileConfiguration
        private set

    // config.ymlを読み込む
    fun loadConfig() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()

        val config = plugin.config
        showOnFirstJoin = config.getBoolean("show-on-first-join", true)
        maxPlaytime = config.getLong("tag.max-playtime", 180L)
        buildingColor = config.getString("tag.purpose-colors.building") ?: "&a"
        sightseeingColor = config.getString("tag.purpose-colors.sightseeing") ?: "&b"
        genreColor = config.getString("tag.genre-color") ?: "&6"
    }

    // ダイアログYAMLファイルを読み込む
    fun loadDialogs() {
        dialogsJa = loadResourceFile("dialogs_ja.yml")
        dialogsEn = loadResourceFile("dialogs_en.yml")
    }

    // フォームYAMLファイルを読み込む（Bedrock向け）
    fun loadForms() {
        formsJa = loadResourceFile("forms_ja.yml")
        formsEn = loadResourceFile("forms_en.yml")
    }

    // リソースファイルをプラグインフォルダから読み込む（なければデフォルトをコピー）
    private fun loadResourceFile(fileName: String): FileConfiguration {
        val file = File(plugin.dataFolder, fileName)
        if (!file.exists()) {
            plugin.saveResource(fileName, false)
        }
        return YamlConfiguration.loadConfiguration(file)
    }

    // 全設定ファイルをデフォルトから再生成する
    fun regenerateFiles() {
        listOf("config.yml", "dialogs_ja.yml", "dialogs_en.yml", "forms_ja.yml", "forms_en.yml")
            .forEach { fileName ->
                File(plugin.dataFolder, fileName).delete()
                plugin.saveResource(fileName, true)
            }
        loadConfig()
        loadDialogs()
        loadForms()
    }

    // 指定したファイルのみデフォルトから再生成する
    // target: "config" / "dialogs_ja" / "dialogs_en" / "forms_ja" / "forms_en"
    fun regenerateFile(target: String) {
        val fileName = when (target) {
            "config"     -> "config.yml"
            "dialogs_ja" -> "dialogs_ja.yml"
            "dialogs_en" -> "dialogs_en.yml"
            "forms_ja"   -> "forms_ja.yml"
            "forms_en"   -> "forms_en.yml"
            else         -> return
        }
        File(plugin.dataFolder, fileName).delete()
        plugin.saveResource(fileName, true)
    }
}