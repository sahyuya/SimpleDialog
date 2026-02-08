package com.github.sahyuya.simpleDialog.dialog

import com.github.sahyuya.simpleDialog.SimpleDialog
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.configuration.ConfigurationSection

class DialogManager(private val plugin: SimpleDialog) {

    private data class DialogSession(
        var currentScreen: String,
        var language: String,
        var selectedPurpose: String?
    )

    private val sessions = mutableMapOf<Player, DialogSession>()

    fun showWelcomeDialog(player: Player) {
        val session = DialogSession("welcome", "ja", null)
        sessions[player] = session
        showDialog(player, "welcome")
    }

    fun showDialog(player: Player, screenId: String) {
        val session = sessions[player] ?: return
        val config = if (session.language == "ja") {
            plugin.configManager.dialogsJa
        } else {
            plugin.configManager.dialogsEn
        }

        val screen = config.getConfigurationSection(screenId) ?: return
        val title = screen.getString("title") ?: "Dialog"
        val textList = screen.getStringList("text")
        val buttonsSection = screen.getConfigurationSection("buttons")

        // Convert color codes
        val formattedTitle = org.bukkit.ChatColor.translateAlternateColorCodes('&', title)
        val formattedText = textList.map {
            org.bukkit.ChatColor.translateAlternateColorCodes('&', it)
        }

        // Build dialog using Paper's Dialog API
        // For now, we'll use a simplified approach with chat messages
        // In actual implementation, use Paper's Dialog API
        player.sendMessage(Component.text("=== $formattedTitle ==="))
        formattedText.forEach { player.sendMessage(Component.text(it)) }
        player.sendMessage(Component.text("================"))

        // Handle buttons
        buttonsSection?.let { handleButtons(player, it, session) }
    }

    private fun handleButtons(player: Player, buttonsSection: ConfigurationSection, session: DialogSession) {
        for (buttonKey in buttonsSection.getKeys(false)) {
            val button = buttonsSection.getConfigurationSection(buttonKey) ?: continue
            val text = button.getString("text") ?: continue
            val formattedText = org.bukkit.ChatColor.translateAlternateColorCodes('&', text)
            val type = button.getString("type") ?: "LINK"
            val next = button.getString("next")

            when (buttonKey) {
                "language_switch" -> {
                    player.sendMessage(Component.text("[$formattedText] - Switch language"))
                }
                "building" -> {
                    player.sendMessage(Component.text("[$formattedText] - Select building purpose"))
                    session.selectedPurpose = "building"
                }
                "sightseeing" -> {
                    player.sendMessage(Component.text("[$formattedText] - Select sightseeing purpose"))
                    session.selectedPurpose = "sightseeing"
                }
                "ok" -> {
                    player.sendMessage(Component.text("[$formattedText] - Continue"))
                    handleOkButton(player, session, next)
                }
                "genre" -> {
                    player.sendMessage(Component.text("[$formattedText] - Select genres"))
                }
                "skip" -> {
                    player.sendMessage(Component.text("[$formattedText] - Skip"))
                }
                "back" -> {
                    player.sendMessage(Component.text("[$formattedText] - Go back"))
                }
            }
        }
    }

    private fun handleOkButton(player: Player, session: DialogSession, next: String?) {
        if (next != null) {
            showDialog(player, next)
        } else {
            // Determine next screen based on purpose
            when (session.currentScreen) {
                "rules" -> {
                    val nextScreen = when (session.selectedPurpose) {
                        "building" -> if (session.language == "ja") "building" else "building_en"
                        "sightseeing" -> if (session.language == "ja") "sightseeing" else "sightseeing_en"
                        else -> return
                    }

                    // Save purpose
                    session.selectedPurpose?.let {
                        plugin.playerDataManager.setPurpose(player.uniqueId, it)
                    }

                    showDialog(player, nextScreen)
                }
                else -> {
                    closeDialog(player)
                }
            }
        }
    }

    fun handleLanguageSwitch(player: Player) {
        val session = sessions[player] ?: return
        session.language = if (session.language == "ja") "en" else "ja"
        val nextScreen = if (session.language == "ja") "welcome" else "welcome_en"
        showDialog(player, nextScreen)
    }

    fun handleGenreSelection(player: Player, genres: List<String>) {
        plugin.playerDataManager.setGenres(player.uniqueId, genres)
        closeDialog(player)
    }

    fun closeDialog(player: Player) {
        sessions.remove(player)
        plugin.tagManager.updateTag(player)
    }
}