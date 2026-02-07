package com.github.sahyuya.simpleDialog

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

enum class Language(val id: String) {
    JA("ja"),
    EN("en");

    // 言語切り替え用の簡易トグル。
    fun toggle(): Language = if (this == JA) EN else JA
}

data class LanguageContent(
    val welcomeTitle: String,
    val welcomeBody: List<String>,
    val welcomeToggleLabel: String,
    val welcomeBuildLabel: String,
    val welcomeSightseeingLabel: String,
    val rulesTitle: String,
    val rulesBody: List<String>,
    val rulesOkLabel: String,
    val buildTitle: String,
    val buildBody: List<String>,
    val buildOkLabel: String,
    val sightseeingTitle: String,
    val sightseeingBody: List<String>,
    val sightseeingSelectGenreLabel: String,
    val sightseeingOkLabel: String,
    val genresTitle: String,
    val genresBody: List<String>,
    val genresBackLabel: String
)

data class GenreTag(val id: String, val label: String)

data class PurposeTags(val build: String, val sightseeing: String)

// dialog.ymlの文言とタグ設定を読み込む。
class DialogConfig(private val plugin: JavaPlugin) {
    private val file = File(plugin.dataFolder, "dialog.yml")
    private var contentByLanguage: Map<Language, LanguageContent> = emptyMap()
    private var purposeTags: PurposeTags = PurposeTags("建築目的", "観光目的")
    private var genreTags: List<GenreTag> = defaultGenreTags()
    private var closeLabel: String = "Close"

    fun reload() {
        if (!file.exists()) {
            plugin.saveResource("dialog.yml", false)
        }
        val config = YamlConfiguration.loadConfiguration(file)
        contentByLanguage = Language.entries.associateWith { readLanguage(config, it) }
        purposeTags = readPurposeTags(config)
        genreTags = readGenreTags(config)
        closeLabel = config.getString("common.close")?.takeIf { it.isNotBlank() } ?: "Close"
    }

    fun languageContent(language: Language): LanguageContent {
        return contentByLanguage[language] ?: contentByLanguage[Language.JA] ?: readLanguage(
            YamlConfiguration.loadConfiguration(file),
            Language.JA
        )
    }

    fun purposeTags(): PurposeTags = purposeTags

    fun genreTags(): List<GenreTag> = genreTags

    fun closeLabel(): String = closeLabel

    private fun readLanguage(config: FileConfiguration, language: Language): LanguageContent {
        val base = "languages.${language.id}"
        fun text(path: String) = config.getString("$base.$path") ?: ""
        fun lines(path: String) = config.getStringList("$base.$path")
        return LanguageContent(
            welcomeTitle = text("welcome.title"),
            welcomeBody = lines("welcome.body"),
            welcomeToggleLabel = text("welcome.buttons.toggle-language"),
            welcomeBuildLabel = text("welcome.buttons.build"),
            welcomeSightseeingLabel = text("welcome.buttons.sightseeing"),
            rulesTitle = text("rules.title"),
            rulesBody = lines("rules.body"),
            rulesOkLabel = text("rules.buttons.ok"),
            buildTitle = text("build.title"),
            buildBody = lines("build.body"),
            buildOkLabel = text("build.buttons.ok"),
            sightseeingTitle = text("sightseeing.title"),
            sightseeingBody = lines("sightseeing.body"),
            sightseeingSelectGenreLabel = text("sightseeing.buttons.select-genre"),
            sightseeingOkLabel = text("sightseeing.buttons.ok"),
            genresTitle = text("genres.title"),
            genresBody = lines("genres.body"),
            genresBackLabel = text("genres.buttons.back")
        )
    }

    private fun readPurposeTags(config: FileConfiguration): PurposeTags {
        val build = config.getString("tags.purpose.build")?.takeIf { it.isNotBlank() } ?: "建築目的"
        val sightseeing = config.getString("tags.purpose.sightseeing")?.takeIf { it.isNotBlank() } ?: "観光目的"
        return PurposeTags(build, sightseeing)
    }

    private fun readGenreTags(config: FileConfiguration): List<GenreTag> {
        val entries = config.getMapList("tags.genres").mapNotNull { raw ->
            val id = raw["id"]?.toString()?.trim().orEmpty()
            val label = raw["label"]?.toString()?.trim().orEmpty()
            if (id.isBlank() || label.isBlank()) null else GenreTag(id, label)
        }
        return if (entries.isNotEmpty()) entries else defaultGenreTags()
    }

    private fun defaultGenreTags(): List<GenreTag> {
        return listOf(
            GenreTag("japanese", "#和風"),
            GenreTag("chinese", "#中華建築"),
            GenreTag("fantasy", "#ファンタジー"),
            GenreTag("western", "#洋風"),
            GenreTag("modern", "#現代建築"),
            GenreTag("sculpture", "#造形")
        )
    }
}
