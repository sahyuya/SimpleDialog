package com.github.sahyuya.simpleDialog.dialog

import com.github.sahyuya.simpleDialog.SimpleDialog
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.body.DialogBody
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
        var selectedPurpose: String?,
        val selectedGenres: MutableList<String>
    )

    private val sessions = mutableMapOf<UUID, DialogSession>()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    fun showWelcomeDialog(player: Player) {
        val session = DialogSession("welcome", "ja", null, mutableListOf())
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

        // Special handling for genres screen
        if (screenId == "genres" || screenId == "genres_en") {
            showGenresDialog(player, screen, session)
            return
        }

        val dialog = createDialog(player, screen, session, screenId)
        player.showDialog(dialog)
    }

    private fun createDialog(player: Player, screen: ConfigurationSection, session: DialogSession, screenId: String): Dialog {
        return Dialog.create { factory ->
            factory.empty()
                .base(createDialogBase(screen))
                .type(createDialogType(player, screen, session, screenId))
        }
    }

    private fun createDialogBase(screen: ConfigurationSection): DialogBase {
        val title = screen.getString("title") ?: "Dialog"
        val textList = screen.getStringList("text")

        val titleComponent = legacySerializer.deserialize(title)
        val bodyComponents = mutableListOf<DialogBody>()

        // Add text lines as body
        textList.forEach { line ->
            bodyComponents.add(DialogBody.plainMessage(legacySerializer.deserialize(line)))
        }

        return DialogBase.builder(titleComponent)
            .canCloseWithEscape(true)
            .body(bodyComponents)
            .build()
    }

    private fun createDialogType(player: Player, screen: ConfigurationSection, session: DialogSession, screenId: String): DialogType {
        val buttonsSection = screen.getConfigurationSection("buttons")

        // If no buttons, create empty multi-action
        if (buttonsSection == null) {
            return DialogType.multiAction(emptyList()).build()
        }

        val actionButtons = mutableListOf<ActionButton>()

        for (buttonKey in buttonsSection.getKeys(false)) {
            val button = buttonsSection.getConfigurationSection(buttonKey) ?: continue
            val text = button.getString("text") ?: continue
            val buttonComponent = legacySerializer.deserialize(text).decoration(TextDecoration.ITALIC, false)

            val actionKey = Key.key("simpledialog", "${screenId}_${buttonKey}")
            val action = DialogAction.customClick(actionKey, null)

            val actionButton = ActionButton.builder(buttonComponent)
                .action(action)
                .width(200)
                .build()

            actionButtons.add(actionButton)
        }

        return DialogType.multiAction(actionButtons).build()
    }

    private fun showGenresDialog(player: Player, screen: ConfigurationSection, session: DialogSession) {
        // For genres, we'll show a simple multi-action dialog
        // Players will click genre buttons to toggle selection
        val title = screen.getString("title") ?: "Genre Selection"
        val textList = screen.getStringList("text")
        val genres = screen.getStringList("genres")

        val titleComponent = legacySerializer.deserialize(title)
        val bodyComponents = mutableListOf<DialogBody>()

        // Add instruction text
        textList.forEach { line ->
            bodyComponents.add(DialogBody.plainMessage(legacySerializer.deserialize(line)))
        }

        // Add currently selected genres
        if (session.selectedGenres.isNotEmpty()) {
            bodyComponents.add(DialogBody.plainMessage(Component.empty()))
            bodyComponents.add(DialogBody.plainMessage(
                legacySerializer.deserialize("&a選択中: ${session.selectedGenres.joinToString(", ")}")
            ))
        }

        val actionButtons = mutableListOf<ActionButton>()

        // Add genre toggle buttons
        genres.forEach { genre ->
            val cleanGenre = genre.replace("&[0-9a-fk-or]".toRegex(), "").trim()
            val isSelected = session.selectedGenres.contains(cleanGenre)
            val prefix = if (isSelected) "&a✓ " else "&7"
            val buttonText = legacySerializer.deserialize("$prefix$genre")
                .decoration(TextDecoration.ITALIC, false)

            val actionKey = Key.key("simpledialog", "genre_toggle_${cleanGenre.replace(" ", "_")}")
            val action = DialogAction.customClick(actionKey, null)

            actionButtons.add(
                ActionButton.builder(buttonText)
                    .action(action)
                    .width(200)
                    .build()
            )
        }

        // Add control buttons
        val buttonsSection = screen.getConfigurationSection("buttons")
        buttonsSection?.let { buttons ->
            actionButtons.add(
                ActionButton.builder(Component.empty())
                    .action(null)
                    .width(200)
                    .build()
            )

            for (buttonKey in buttons.getKeys(false)) {
                val button = buttons.getConfigurationSection(buttonKey) ?: continue
                val text = button.getString("text") ?: continue
                val buttonComponent = legacySerializer.deserialize(text)
                    .decoration(TextDecoration.ITALIC, false)

                val actionKey = Key.key("simpledialog", "genres_${buttonKey}")
                val action = DialogAction.customClick(actionKey, null)

                actionButtons.add(
                    ActionButton.builder(buttonComponent)
                        .action(action)
                        .width(200)
                        .build()
                )
            }
        }

        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(titleComponent)
                        .canCloseWithEscape(true)
                        .body(bodyComponents)
                        .build()
                )
                .type(DialogType.multiAction(actionButtons).build())
        }

        player.showDialog(dialog)
    }

    fun handleDialogAction(player: Player, actionKey: Key) {
        val session = sessions[player.uniqueId] ?: return
        val currentScreen = session.currentScreen

        val keyString = actionKey.value()
        plugin.logger.info("Dialog action: $keyString for player ${player.name} on screen $currentScreen")

        // Parse action
        val parts = keyString.split("_")
        if (parts.isEmpty()) return

        when {
            // Genre toggle
            keyString.startsWith("genre_toggle_") -> {
                val genreName = keyString.removePrefix("genre_toggle_").replace("_", " ")
                toggleGenre(session, genreName)
                showDialog(player, currentScreen) // Refresh dialog
            }

            // Genres control buttons
            keyString.startsWith("genres_") -> {
                val buttonKey = keyString.removePrefix("genres_")
                when (buttonKey) {
                    "back" -> {
                        goBack(player, session)
                    }
                    "ok" -> {
                        saveGenresAndClose(player, session)
                    }
                }
            }

            // Regular screen buttons
            else -> {
                handleScreenButton(player, session, currentScreen, parts.lastOrNull() ?: "")
            }
        }
    }

    private fun toggleGenre(session: DialogSession, genreName: String) {
        if (session.selectedGenres.contains(genreName)) {
            session.selectedGenres.remove(genreName)
        } else {
            session.selectedGenres.add(genreName)
        }
    }

    private fun handleScreenButton(player: Player, session: DialogSession, screenId: String, buttonKey: String) {
        val config = if (session.language == "ja") {
            plugin.configManager.dialogsJa
        } else {
            plugin.configManager.dialogsEn
        }

        val screen = config.getConfigurationSection(screenId) ?: return
        val buttonsSection = screen.getConfigurationSection("buttons") ?: return
        val button = buttonsSection.getConfigurationSection(buttonKey) ?: return

        val next = button.getString("next")

        when (buttonKey) {
            "language_switch", "languageswitch" -> {
                session.language = if (session.language == "ja") "en" else "ja"
                val nextScreen = if (session.language == "ja") "welcome" else "welcome_en"
                showDialog(player, nextScreen)
            }
            "building" -> {
                session.selectedPurpose = "building"
                plugin.playerDataManager.setPurpose(player.uniqueId, "building")
                next?.let { showDialog(player, it) } ?: determineNextFromRules(player, session)
            }
            "sightseeing" -> {
                session.selectedPurpose = "sightseeing"
                plugin.playerDataManager.setPurpose(player.uniqueId, "sightseeing")
                next?.let { showDialog(player, it) } ?: determineNextFromRules(player, session)
            }
            "ok" -> {
                handleOkButton(player, session, next)
            }
            "genre" -> {
                next?.let { showDialog(player, it) }
            }
            "skip" -> {
                closeDialog(player)
            }
            "back" -> {
                next?.let { showDialog(player, it) }
            }
        }
    }

    private fun handleOkButton(player: Player, session: DialogSession, next: String?) {
        if (next != null) {
            showDialog(player, next)
        } else {
            determineNextFromRules(player, session)
        }
    }

    private fun determineNextFromRules(player: Player, session: DialogSession) {
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

    private fun goBack(player: Player, session: DialogSession) {
        val previousScreen = if (session.language == "ja") "sightseeing" else "sightseeing_en"
        showDialog(player, previousScreen)
    }

    private fun saveGenresAndClose(player: Player, session: DialogSession) {
        if (session.selectedGenres.isNotEmpty()) {
            plugin.playerDataManager.setGenres(player.uniqueId, session.selectedGenres)
        }
        closeDialog(player)
    }

    fun closeDialog(player: Player) {
        sessions.remove(player.uniqueId)
        plugin.tagManager.updateTag(player)
    }
}