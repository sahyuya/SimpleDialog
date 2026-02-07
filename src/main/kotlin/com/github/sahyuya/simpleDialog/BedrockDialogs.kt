package com.github.sahyuya.simpleDialog

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.floodgate.api.FloodgateApi

interface BedrockDialogs {
    // Floodgate判定とフォーム表示の窓口。
    fun isBedrockPlayer(player: Player): Boolean

    fun showWelcome(
        player: Player,
        content: LanguageContent,
        onToggle: () -> Unit,
        onBuild: () -> Unit,
        onSightseeing: () -> Unit
    )

    fun showRules(player: Player, content: LanguageContent, onOk: () -> Unit)

    fun showBuild(player: Player, content: LanguageContent, onOk: () -> Unit)

    fun showSightseeing(
        player: Player,
        content: LanguageContent,
        onSelectGenres: () -> Unit,
        onOk: () -> Unit
    )

    fun showGenres(
        player: Player,
        content: LanguageContent,
        tags: List<GenreTag>,
        selected: Set<String>,
        onBack: (Set<String>) -> Unit
    )
}

object BedrockDialogsFactory {
    fun tryCreate(plugin: JavaPlugin): BedrockDialogs? {
        if (!plugin.server.pluginManager.isPluginEnabled("floodgate")) {
            return null
        }
        return try {
            BedrockDialogsImpl()
        } catch (error: NoClassDefFoundError) {
            plugin.logger.warning("FloodgateまたはCumulusが見つからないため、Bedrockフォームは無効です。")
            null
        }
    }
}

private class BedrockDialogsImpl : BedrockDialogs {
    private val api = FloodgateApi.getInstance()

    override fun isBedrockPlayer(player: Player): Boolean = api.isFloodgatePlayer(player.uniqueId)

    override fun showWelcome(
        player: Player,
        content: LanguageContent,
        onToggle: () -> Unit,
        onBuild: () -> Unit,
        onSightseeing: () -> Unit
    ) {
        val form = SimpleForm.builder()
            .title(TextFormat.legacy(content.welcomeTitle))
            .content(TextFormat.legacyLines(content.welcomeBody))
            .button(TextFormat.legacy(content.welcomeToggleLabel))
            .button(TextFormat.legacy(content.welcomeBuildLabel))
            .button(TextFormat.legacy(content.welcomeSightseeingLabel))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> onToggle()
                    1 -> onBuild()
                    2 -> onSightseeing()
                }
            }
            .build()
        sendForm(player, form)
    }

    override fun showRules(player: Player, content: LanguageContent, onOk: () -> Unit) {
        val form = SimpleForm.builder()
            .title(TextFormat.legacy(content.rulesTitle))
            .content(TextFormat.legacyLines(content.rulesBody))
            .button(TextFormat.legacy(content.rulesOkLabel))
            .validResultHandler { onOk() }
            .build()
        sendForm(player, form)
    }

    override fun showBuild(player: Player, content: LanguageContent, onOk: () -> Unit) {
        val form = SimpleForm.builder()
            .title(TextFormat.legacy(content.buildTitle))
            .content(TextFormat.legacyLines(content.buildBody))
            .button(TextFormat.legacy(content.buildOkLabel))
            .validResultHandler { onOk() }
            .build()
        sendForm(player, form)
    }

    override fun showSightseeing(
        player: Player,
        content: LanguageContent,
        onSelectGenres: () -> Unit,
        onOk: () -> Unit
    ) {
        val form = SimpleForm.builder()
            .title(TextFormat.legacy(content.sightseeingTitle))
            .content(TextFormat.legacyLines(content.sightseeingBody))
            .button(TextFormat.legacy(content.sightseeingSelectGenreLabel))
            .button(TextFormat.legacy(content.sightseeingOkLabel))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> onSelectGenres()
                    1 -> onOk()
                }
            }
            .build()
        sendForm(player, form)
    }

    override fun showGenres(
        player: Player,
        content: LanguageContent,
        tags: List<GenreTag>,
        selected: Set<String>,
        onBack: (Set<String>) -> Unit
    ) {
        val builder = CustomForm.builder()
            .title(TextFormat.legacy(content.genresTitle))

        if (content.genresBody.isNotEmpty()) {
            builder.label(TextFormat.legacyLines(content.genresBody))
        }

        for (tag in tags) {
            builder.toggle(TextFormat.legacy(tag.label), selected.contains(tag.id))
        }

        builder.closedOrInvalidResultHandler(Runnable { onBack(selected) })
        builder.validResultHandler { response ->
            response.includeLabels(true)
            val offset = if (content.genresBody.isNotEmpty()) 1 else 0
            val nextSelected = mutableSetOf<String>()
            tags.forEachIndexed { index, tag ->
                if (response.getToggle(index + offset)) {
                    nextSelected.add(tag.id)
                }
            }
            onBack(nextSelected)
        }

        sendForm(player, builder.build())
    }

    private fun sendForm(player: Player, form: Form) {
        // オンライン中のプレイヤーにのみ送信する。
        if (!player.isOnline) {
            return
        }
        api.sendForm(player.uniqueId, form)
    }
}
