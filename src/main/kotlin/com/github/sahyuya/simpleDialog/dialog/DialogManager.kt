package com.github.sahyuya.simpleDialog.dialog

import com.github.sahyuya.simpleDialog.SimpleDialog
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import io.papermc.paper.registry.data.dialog.action.DialogAction
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import java.util.*

class DialogManager(private val plugin: SimpleDialog) {

    private data class DialogSession(
        var currentScreen: String,
        var language: String,
        var selectedPurpose: String?
    )

    private val sessions = mutableMapOf<UUID, DialogSession>()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    fun showWelcomeDialog(player: Player) {
        val session = DialogSession("welcome", "ja", null)
        sessions[player.uniqueId] = session
        showDialog(player, "welcome")
    }

    private fun showDialog(player: Player, screenId: String) {
        val session = sessions[player.uniqueId] ?: return
        session.currentScreen = screenId

        val config = if (session.language == "ja") {
            plugin.configManager.dialogsJa
        } else {
            plugin.configManager.dialogsEn
        }

        val screen = config.getConfigurationSection(screenId) ?: return

        val dialog = createDialog(player, screen, session, screenId)
        player.showDialog(dialog)
    }

    private fun createDialog(player: Player, screen: ConfigurationSection, session: DialogSession, screenId: String): Dialog {
        return Dialog.create { factory ->
            factory.empty()
                .base(createDialogBase(screen, screenId))
                .type(createDialogType(screen, screenId))
        }
    }

    private fun createDialogBase(screen: ConfigurationSection, screenId: String): DialogBase {
        val title = screen.getString("title") ?: "Dialog"
        val sections = screen.getList("sections") as? List<Map<String, Any>> ?: emptyList()

        val titleComponent = legacySerializer.deserialize(title)
        val bodyComponents = mutableListOf<DialogBody>()
        val inputs = mutableListOf<DialogInput>()

        // Get current session to check for selected genres
        val session = sessions.values.firstOrNull { it.currentScreen == screenId }

        // Process sections in order
        sections.forEach { section ->
            val type = section["type"] as? String ?: return@forEach

            when (type) {
                "text" -> {
                    val lines = section["lines"] as? List<String> ?: return@forEach
                    lines.forEach { line ->
                        bodyComponents.add(
                            DialogBody.plainMessage(
                                legacySerializer.deserialize(line)
                                    .decoration(TextDecoration.ITALIC, false)
                            )
                        )
                    }
                }
                "checkboxes" -> {
                    val items = section["items"] as? List<Map<String, Any>> ?: return@forEach
                    items.forEach { item ->
                        val key = item["key"] as? String ?: return@forEach
                        val text = item["text"] as? String ?: return@forEach
                        val genre = item["genre"] as? String ?: ""

                        // Check if this genre is already selected
                        val isSelected = session?.let { sess ->
                            // Get current genres from player data
                            val uuid = sessions.entries.find { it.value == sess }?.key
                            uuid?.let { plugin.playerDataManager.getGenres(it)?.contains(genre) } ?: false
                        } ?: false

                        val displayText = if (isSelected) {
                            "&a✓ $text"
                        } else {
                            text
                        }

                        val label = legacySerializer.deserialize(displayText)
                            .decoration(TextDecoration.ITALIC, false)

                        val input = DialogInput.bool(key, label)
                            .initial(isSelected)
                            .build()

                        inputs.add(input)
                    }
                }
            }
        }

        val builder = DialogBase.builder(titleComponent)
            .canCloseWithEscape(true)
            .body(bodyComponents)

        if (inputs.isNotEmpty()) {
            builder.inputs(inputs)
        }

        return builder.build()
    }

    private fun createDialogType(screen: ConfigurationSection, screenId: String): DialogType {
        val sections = screen.getList("sections") as? List<Map<String, Any>> ?: emptyList()
        val actionButtons = mutableListOf<ActionButton>()

        // Process sections and collect buttons
        sections.forEach { section ->
            val type = section["type"] as? String ?: return@forEach

            if (type == "buttons") {
                val items = section["items"] as? List<Map<String, Any>> ?: return@forEach
                items.forEach { item ->
                    val key = item["key"] as? String ?: return@forEach
                    val text = item["text"] as? String ?: return@forEach

                    val buttonText = legacySerializer.deserialize(text)
                        .decoration(TextDecoration.ITALIC, false)

                    val actionKey = Key.key("simpledialog", "${screenId}_${key}")
                    val action = DialogAction.customClick(actionKey, null)

                    val button = ActionButton.builder(buttonText)
                        .action(action)
                        .width(200)
                        .build()

                    actionButtons.add(button)
                }
            }
        }

        return DialogType.multiAction(actionButtons).build()
    }

    fun handleDialogAction(player: Player, actionKey: Key, responseView: DialogResponseView?) {
        val session = sessions[player.uniqueId] ?: return
        val currentScreen = session.currentScreen

        val keyString = actionKey.value()
        plugin.logger.info("ダイアログアクション処理: $keyString")

        // 画面遷移前にチェックボックスの状態を保存する
        if (responseView != null) {
            saveCheckboxes(player, session, currentScreen, responseView)
        }

        val config = if (session.language == "ja") {
            plugin.configManager.dialogsJa
        } else {
            plugin.configManager.dialogsEn
        }

        val screen = config.getConfigurationSection(currentScreen) ?: return
        val sections = screen.getList("sections") as? List<Map<String, Any>> ?: emptyList()

        // アクションキーからボタンキーを抽出する（形式: "screenId_buttonKey"）
        val buttonKey = keyString.substringAfter("${currentScreen}_")
        plugin.logger.info("ボタンキー: $buttonKey")

        // sectionsからボタンのデータを検索する
        var buttonData: Map<String, Any>? = null
        for (section in sections) {
            val type = section["type"] as? String ?: continue
            if (type == "buttons") {
                val items = section["items"] as? List<Map<String, Any>> ?: continue
                buttonData = items.find { (it["key"] as? String) == buttonKey }
                if (buttonData != null) break
            }
        }

        if (buttonData == null) {
            plugin.logger.warning("ボタンが見つかりません: $buttonKey (画面: $currentScreen)")
            return
        }

        val action = buttonData["action"] as? String ?: "close"
        plugin.logger.info("ボタンアクション: $action")

        when (action) {
            "switch_language" -> {
                session.language = if (session.language == "ja") "en" else "ja"
                val nextScreen = if (session.language == "ja") "welcome" else "welcome_en"
                showDialog(player, nextScreen)
            }
            "select_purpose" -> {
                val purpose = buttonData["purpose"] as? String
                val next = buttonData["next"] as? String

                purpose?.let {
                    session.selectedPurpose = it
                    plugin.playerDataManager.setPurpose(player.uniqueId, it)
                }

                next?.let { showDialog(player, it) } ?: closeDialog(player)
            }
            "next" -> {
                determineNextScreen(player, session)
            }
            "goto" -> {
                val next = buttonData["next"] as? String
                next?.let { showDialog(player, it) } ?: closeDialog(player)
            }
            "close" -> {
                closeDialog(player)
            }
            "back" -> {
                val next = buttonData["next"] as? String
                next?.let { showDialog(player, it) } ?: closeDialog(player)
            }
        }
    }

    // チェックボックスの状態をプレイヤーデータに保存し、タグを即座に更新する
    private fun saveCheckboxes(player: Player, session: DialogSession, screenId: String, responseView: DialogResponseView) {
        val config = if (session.language == "ja") {
            plugin.configManager.dialogsJa
        } else {
            plugin.configManager.dialogsEn
        }

        val screen = config.getConfigurationSection(screenId) ?: return
        val sections = screen.getList("sections") as? List<Map<String, Any>> ?: emptyList()

        val selectedGenres = mutableListOf<String>()

        // checkboxesセクションからチェック状態を取得する
        for (section in sections) {
            val type = section["type"] as? String ?: continue
            if (type == "checkboxes") {
                val items = section["items"] as? List<Map<String, Any>> ?: continue
                items.forEach { item ->
                    val key = item["key"] as? String ?: return@forEach
                    val genre = item["genre"] as? String ?: return@forEach

                    val isChecked = responseView.getBoolean(key) ?: false
                    if (isChecked) {
                        selectedGenres.add(genre)
                    }
                }
            }
        }

        plugin.logger.info("${player.name} のジャンル選択を保存します: $selectedGenres")

        // 空配列でも上書きして確実に反映（チェックを全て外した場合も対応）
        plugin.playerDataManager.setGenres(player.uniqueId, selectedGenres)
        plugin.playerDataManager.saveData()

        // タグを即座に更新する
        plugin.tagManager.updateTag(player)
    }

    private fun determineNextScreen(player: Player, session: DialogSession) {
        when (session.currentScreen) {
            "rules", "rules_en" -> {
                val nextScreen = when (session.selectedPurpose) {
                    "building" -> if (session.language == "ja") "building" else "building_en"
                    "sightseeing" -> if (session.language == "ja") "sightseeing" else "sightseeing_en"
                    else -> {
                        closeDialog(player)
                        return
                    }
                }
                showDialog(player, nextScreen)
            }
            else -> {
                closeDialog(player)
            }
        }
    }

    fun closeDialog(player: Player) {
        sessions.remove(player.uniqueId)
        plugin.tagManager.updateTag(player)
    }
}