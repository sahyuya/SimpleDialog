package com.github.sahyuya.simpleDialog

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import java.time.Duration
import java.util.UUID

enum class Purpose {
    BUILD,
    SIGHTSEE
}

data class PlayerSession(
    var language: Language = Language.JA,
    var purpose: Purpose? = null,
    var selectedGenres: MutableSet<String> = mutableSetOf()
)

// ダイアログの画面遷移と選択状態を管理する。
class DialogManager(
    private val dialogConfig: DialogConfig,
    private val tagManager: TagManager
) {
    // ダイアログの進行状態をプレイヤー単位で保持する。
    private val sessions = mutableMapOf<UUID, PlayerSession>()
    private val dialogProvider = DialogInstancesProvider.instance()
    private val clickOptions = ClickCallback.Options.builder()
        .uses(1)
        .lifetime(Duration.ofMinutes(10))
        .build()
    private var bedrockDialogs: BedrockDialogs? = null

    fun setBedrockDialogs(bedrockDialogs: BedrockDialogs?) {
        this.bedrockDialogs = bedrockDialogs
    }

    fun handleJoin(player: Player) {
        sessions.putIfAbsent(player.uniqueId, PlayerSession())
    }

    fun handleQuit(player: Player) {
        sessions.remove(player.uniqueId)
    }

    fun resetSession(player: Player) {
        sessions.remove(player.uniqueId)
    }

    fun showWelcome(player: Player) {
        val session = session(player)
        val content = dialogConfig.languageContent(session.language)
        val bedrock = bedrockDialogs
        if (bedrock != null && bedrock.isBedrockPlayer(player)) {
            bedrock.showWelcome(
                player,
                content,
                onToggle = { toggleLanguage(player) },
                onBuild = { setPurposeAndShowRules(player, Purpose.BUILD) },
                onSightseeing = { setPurposeAndShowRules(player, Purpose.SIGHTSEE) }
            )
            return
        }
        showWelcomePaper(player, content)
    }

    fun showRules(player: Player) {
        val session = session(player)
        val content = dialogConfig.languageContent(session.language)
        val bedrock = bedrockDialogs
        if (bedrock != null && bedrock.isBedrockPlayer(player)) {
            bedrock.showRules(player, content) { showNextAfterRules(player) }
            return
        }
        val okButton = actionButton(content.rulesOkLabel) { _, audience ->
            val target = audience as? Player ?: return@actionButton
            showNextAfterRules(target)
        }
        val dialog = buildDialog(
            title = content.rulesTitle,
            body = content.rulesBody,
            inputs = emptyList(),
            type = dialogProvider.notice(okButton)
        )
        player.showDialog(dialog)
    }

    fun showBuild(player: Player) {
        val session = session(player)
        val content = dialogConfig.languageContent(session.language)
        val bedrock = bedrockDialogs
        if (bedrock != null && bedrock.isBedrockPlayer(player)) {
            bedrock.showBuild(player, content) { finalizeTags(player) }
            return
        }
        val okButton = actionButton(content.buildOkLabel) { _, audience ->
            val target = audience as? Player ?: return@actionButton
            finalizeTags(target)
        }
        val dialog = buildDialog(
            title = content.buildTitle,
            body = content.buildBody,
            inputs = emptyList(),
            type = dialogProvider.notice(okButton)
        )
        player.showDialog(dialog)
    }

    fun showSightseeing(player: Player) {
        val session = session(player)
        val content = dialogConfig.languageContent(session.language)
        val bedrock = bedrockDialogs
        if (bedrock != null && bedrock.isBedrockPlayer(player)) {
            bedrock.showSightseeing(
                player,
                content,
                onSelectGenres = { showGenres(player) },
                onOk = { finalizeTags(player) }
            )
            return
        }
        val selectGenres = actionButton(content.sightseeingSelectGenreLabel) { _, audience ->
            val target = audience as? Player ?: return@actionButton
            showGenres(target)
        }
        val okButton = actionButton(content.sightseeingOkLabel) { _, audience ->
            val target = audience as? Player ?: return@actionButton
            finalizeTags(target)
        }
        val exitAction = closeActionButton()
        val type = dialogProvider.multiAction(listOf(selectGenres, okButton))
            .exitAction(exitAction)
            .columns(1)
            .build()
        val dialog = buildDialog(
            title = content.sightseeingTitle,
            body = content.sightseeingBody,
            inputs = emptyList(),
            type = type
        )
        player.showDialog(dialog)
    }

    fun showGenres(player: Player) {
        val session = session(player)
        val content = dialogConfig.languageContent(session.language)
        val genres = dialogConfig.genreTags()
        val bedrock = bedrockDialogs
        if (bedrock != null && bedrock.isBedrockPlayer(player)) {
            bedrock.showGenres(
                player,
                content,
                genres,
                session.selectedGenres
            ) { selected ->
                session.selectedGenres = selected.toMutableSet()
                showSightseeing(player)
            }
            return
        }

        val inputs = genres.map { tag ->
            dialogProvider.booleanBuilder(tag.id, TextFormat.component(tag.label))
                .initial(session.selectedGenres.contains(tag.id))
                .onTrue("ON")
                .onFalse("OFF")
                .build()
        }
        val backButton = actionButton(content.genresBackLabel) { view, audience ->
            val target = audience as? Player ?: return@actionButton
            val selected = genres.filter { view.getBoolean(it.id) == true }
                .map { it.id }
                .toMutableSet()
            session(target).selectedGenres = selected
            showSightseeing(target)
        }
        val exitAction = closeActionButton()
        val type = dialogProvider.multiAction(listOf(backButton))
            .exitAction(exitAction)
            .columns(1)
            .build()
        val dialog = buildDialog(
            title = content.genresTitle,
            body = content.genresBody,
            inputs = inputs,
            type = type
        )
        player.showDialog(dialog)
    }

    private fun showWelcomePaper(player: Player, content: LanguageContent) {
        val toggleButton = actionButton(content.welcomeToggleLabel) { _, audience ->
            val target = audience as? Player ?: return@actionButton
            toggleLanguage(target)
        }
        val buildButton = actionButton(content.welcomeBuildLabel) { _, audience ->
            val target = audience as? Player ?: return@actionButton
            setPurposeAndShowRules(target, Purpose.BUILD)
        }
        val sightseeingButton = actionButton(content.welcomeSightseeingLabel) { _, audience ->
            val target = audience as? Player ?: return@actionButton
            setPurposeAndShowRules(target, Purpose.SIGHTSEE)
        }
        val exitAction = closeActionButton()
        val type = dialogProvider.multiAction(listOf(toggleButton, buildButton, sightseeingButton))
            .exitAction(exitAction)
            .columns(1)
            .build()
        val dialog = buildDialog(
            title = content.welcomeTitle,
            body = content.welcomeBody,
            inputs = emptyList(),
            type = type
        )
        player.showDialog(dialog)
    }

    private fun toggleLanguage(player: Player) {
        val session = session(player)
        session.language = session.language.toggle()
        showWelcome(player)
    }

    private fun setPurposeAndShowRules(player: Player, purpose: Purpose) {
        val session = session(player)
        session.purpose = purpose
        showRules(player)
    }

    private fun showNextAfterRules(player: Player) {
        when (session(player).purpose) {
            Purpose.BUILD -> showBuild(player)
            Purpose.SIGHTSEE -> showSightseeing(player)
            null -> showWelcome(player)
        }
    }

    private fun finalizeTags(player: Player) {
        val session = session(player)
        val purpose = session.purpose ?: return
        val purposeTags = dialogConfig.purposeTags()
        // 目的タグとジャンルタグをまとめて保存する。
        val tagLabels = mutableListOf<String>()
        val purposeLabel = if (purpose == Purpose.BUILD) purposeTags.build else purposeTags.sightseeing
        tagLabels.add(purposeLabel)
        if (purpose == Purpose.SIGHTSEE) {
            val selected = session.selectedGenres
            val genreTags = dialogConfig.genreTags()
            for (tag in genreTags) {
                if (selected.contains(tag.id)) {
                    tagLabels.add(tag.label)
                }
            }
        }
        tagManager.setTags(player, tagLabels)
        sessions.remove(player.uniqueId)
    }

    private fun session(player: Player): PlayerSession {
        return sessions.getOrPut(player.uniqueId) { PlayerSession() }
    }

    private fun buildDialog(
        title: String,
        body: List<String>,
        inputs: List<DialogInput>,
        type: DialogType
    ): Dialog {
        val base = dialogProvider.dialogBaseBuilder(TextFormat.component(title))
            .externalTitle(TextFormat.component(title))
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(bodyComponents(body))
            .inputs(inputs)
            .build()
        return Dialog.create { factory ->
            factory.empty().base(base).type(type)
        }
    }

    private fun bodyComponents(lines: List<String>): List<io.papermc.paper.registry.data.dialog.body.DialogBody> {
        if (lines.isEmpty()) {
            return emptyList()
        }
        return listOf(dialogProvider.plainMessageDialogBody(TextFormat.componentLines(lines)))
    }

    private fun actionButton(
        label: String,
        handler: (DialogResponseView, Audience) -> Unit
    ): io.papermc.paper.registry.data.dialog.ActionButton {
        val callback = DialogActionCallback { view, audience -> handler(view, audience) }
        val action = DialogAction.customClick(callback, clickOptions)
        return dialogProvider.actionButtonBuilder(TextFormat.component(label))
            .action(action)
            .build()
    }

    private fun closeActionButton(): io.papermc.paper.registry.data.dialog.ActionButton {
        val callback = DialogActionCallback { _, _ -> }
        val action = DialogAction.customClick(callback, clickOptions)
        return dialogProvider.actionButtonBuilder(TextFormat.component(dialogConfig.closeLabel()))
            .action(action)
            .build()
    }
}
