package com.github.sahyuya.simpleDialog.form

import com.github.sahyuya.simpleDialog.SimpleDialog
import org.bukkit.entity.Player
import org.bukkit.configuration.ConfigurationSection
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.util.FormBuilder
import org.geysermc.floodgate.api.FloodgateApi

class FormManager(private val plugin: SimpleDialog) {

    private data class FormSession(
        var currentScreen: String,
        var language: String,
        var selectedPurpose: String?
    )

    private val sessions = mutableMapOf<Player, FormSession>()

    private fun isBedrockPlayer(player: Player): Boolean {
        return try {
            FloodgateApi.getInstance().isFloodgatePlayer(player.uniqueId)
        } catch (e: Exception) {
            false
        }
    }

    fun showWelcomeForm(player: Player) {
        if (!isBedrockPlayer(player)) return

        val session = FormSession("welcome", "ja", null)
        sessions[player] = session
        showForm(player, "welcome")
    }

    fun showForm(player: Player, screenId: String) {
        if (!isBedrockPlayer(player)) return

        val session = sessions[player] ?: return
        val config = if (session.language == "ja") {
            plugin.configManager.formsJa
        } else {
            plugin.configManager.formsEn
        }

        val screen = config.getConfigurationSection(screenId) ?: return
        val type = screen.getString("type") ?: "SIMPLE_FORM"

        when (type) {
            "SIMPLE_FORM" -> showSimpleForm(player, screen, session, screenId)
            "CUSTOM_FORM" -> showCustomForm(player, screen, session, screenId)
        }
    }

    private fun showSimpleForm(player: Player, screen: ConfigurationSection, session: FormSession, screenId: String) {
        val title = screen.getString("title") ?: "Form"
        val content = screen.getString("content") ?: ""
        val buttons = screen.getList("buttons") as? List<Map<String, Any>> ?: return

        // Convert color codes
        val formattedTitle = org.bukkit.ChatColor.translateAlternateColorCodes('&', title)
        val formattedContent = org.bukkit.ChatColor.translateAlternateColorCodes('&', content)

        val form = SimpleForm.builder()
            .title(formattedTitle)
            .content(formattedContent)

        buttons.forEach { buttonData ->
            val text = buttonData["text"] as? String ?: ""
            val formattedText = org.bukkit.ChatColor.translateAlternateColorCodes('&', text)
            form.button(formattedText)
        }

        form.validResultHandler { response ->
            val clickedIndex = response.clickedButtonId()
            if (clickedIndex >= 0 && clickedIndex < buttons.size) {
                val buttonData = buttons[clickedIndex]
                handleButtonAction(player, buttonData, session)
            }
        }

        form.closedOrInvalidResultHandler(Runnable {
            closeForm(player)
        })

        FloodgateApi.getInstance().sendForm(player.uniqueId, form.build())
    }

    private fun showCustomForm(player: Player, screen: ConfigurationSection, session: FormSession, screenId: String) {
        val title = screen.getString("title") ?: "Form"
        val content = screen.getList("content") as? List<Map<String, Any>> ?: return
        val buttons = screen.getList("buttons") as? List<Map<String, Any>> ?: emptyList()

        // Convert color codes for title
        val formattedTitle = org.bukkit.ChatColor.translateAlternateColorCodes('&', title)

        val form = CustomForm.builder()
            .title(formattedTitle)

        val genreIndices = mutableListOf<Int>()
        var currentIndex = 0

        content.forEach { element ->
            val elementType = element["type"] as? String ?: ""
            when (elementType) {
                "LABEL" -> {
                    val text = element["text"] as? String ?: ""
                    val formattedText = org.bukkit.ChatColor.translateAlternateColorCodes('&', text)
                    form.label(formattedText)
                }
                "TOGGLE" -> {
                    val text = element["text"] as? String ?: ""
                    val formattedText = org.bukkit.ChatColor.translateAlternateColorCodes('&', text)
                    val default = element["default"] as? Boolean ?: false
                    form.toggle(formattedText, default)
                    genreIndices.add(currentIndex)
                    currentIndex++
                }
            }
        }

        form.validResultHandler { response ->
            // Collect selected genres
            val selectedGenres = mutableListOf<String>()
            content.forEachIndexed { index, element ->
                if (element["type"] == "TOGGLE") {
                    val toggleIndex = genreIndices.indexOf(index)
                    if (toggleIndex >= 0 && response.asToggle(toggleIndex)) {
                        val genre = element["genre"] as? String ?: ""
                        selectedGenres.add(genre)
                    }
                }
            }

            // Handle button action (for custom forms, we use the first button as submit)
            if (buttons.isNotEmpty()) {
                handleGenreSubmit(player, selectedGenres, session)
            }
        }

        form.closedOrInvalidResultHandler(Runnable {
            closeForm(player)
        })

        FloodgateApi.getInstance().sendForm(player.uniqueId, form.build())
    }

    private fun handleButtonAction(player: Player, buttonData: Map<String, Any>, session: FormSession) {
        val action = buttonData["action"] as? String ?: ""
        val next = buttonData["next"] as? String
        val purpose = buttonData["purpose"] as? String

        when (action) {
            "SWITCH_LANGUAGE" -> {
                session.language = if (session.language == "ja") "en" else "ja"
                next?.let { showForm(player, it) }
            }
            "SELECT_PURPOSE" -> {
                purpose?.let {
                    session.selectedPurpose = it
                    plugin.playerDataManager.setPurpose(player.uniqueId, it)
                }
                next?.let { showForm(player, it) }
            }
            "NEXT" -> {
                val nextScreen = next ?: determineNextScreen(session)
                nextScreen?.let { showForm(player, it) } ?: closeForm(player)
            }
            "CLOSE" -> {
                closeForm(player)
            }
            "BACK" -> {
                next?.let { showForm(player, it) }
            }
            "SUBMIT" -> {
                closeForm(player)
            }
        }
    }

    private fun handleGenreSubmit(player: Player, genres: List<String>, session: FormSession) {
        plugin.playerDataManager.setGenres(player.uniqueId, genres)
        closeForm(player)
    }

    private fun determineNextScreen(session: FormSession): String? {
        return when (session.currentScreen) {
            "rules", "rules_en" -> {
                when (session.selectedPurpose) {
                    "building" -> if (session.language == "ja") "building" else "building_en"
                    "sightseeing" -> if (session.language == "ja") "sightseeing" else "sightseeing_en"
                    else -> null
                }
            }
            else -> null
        }
    }

    fun closeForm(player: Player) {
        sessions.remove(player)
        plugin.tagManager.updateTag(player)
    }
}