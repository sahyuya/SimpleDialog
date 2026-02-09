package com.github.sahyuya.simpleDialog

import com.github.sahyuya.simpleDialog.command.SimpleDialogCommand
import com.github.sahyuya.simpleDialog.config.ConfigManager
import com.github.sahyuya.simpleDialog.data.PlayerDataManager
import com.github.sahyuya.simpleDialog.dialog.DialogManager
import com.github.sahyuya.simpleDialog.form.FormManager
import com.github.sahyuya.simpleDialog.listener.PlayerJoinListener
import com.github.sahyuya.simpleDialog.listener.DialogClickListener
import com.github.sahyuya.simpleDialog.tag.TagManager
import org.bukkit.plugin.java.JavaPlugin

class SimpleDialog : JavaPlugin() {

    lateinit var configManager: ConfigManager
        private set

    lateinit var playerDataManager: PlayerDataManager
        private set

    lateinit var dialogManager: DialogManager
        private set

    lateinit var formManager: FormManager
        private set

    lateinit var tagManager: TagManager
        private set

    override fun onEnable() {
        // Initialize managers
        configManager = ConfigManager(this)
        playerDataManager = PlayerDataManager(this)
        dialogManager = DialogManager(this)
        formManager = FormManager(this)
        tagManager = TagManager(this)

        // Load configurations
        configManager.loadConfig()
        configManager.loadDialogs()
        configManager.loadForms()
        playerDataManager.loadData()

        // Register listeners
        server.pluginManager.registerEvents(PlayerJoinListener(this), this)
        server.pluginManager.registerEvents(DialogClickListener(this), this)

        // Register commands using Paper's command API
        val commandExecutor = SimpleDialogCommand(this)

        // Register the command using reflection to access CommandMap
        server.scheduler.runTask(this, Runnable {
            try {
                // Create a simple command wrapper
                val command = object : org.bukkit.command.Command(
                    "simpledialog",
                    "SimpleDialog main command",
                    "/simpledialog <reload|enable|disable|cleartag|show|regenerate>",
                    listOf("sd")
                ) {
                    override fun execute(
                        sender: org.bukkit.command.CommandSender,
                        commandLabel: String,
                        args: Array<out String>
                    ): Boolean {
                        return commandExecutor.onCommand(sender, this, commandLabel, args)
                    }

                    override fun tabComplete(
                        sender: org.bukkit.command.CommandSender,
                        alias: String,
                        args: Array<out String>
                    ): List<String> {
                        return commandExecutor.onTabComplete(sender, this, alias, args) ?: emptyList()
                    }
                }

                command.permission = "simpledialog.admin"
                server.commandMap.register("simpledialog", command)

                logger.info("Commands registered successfully!")
            } catch (e: Exception) {
                logger.warning("Failed to register command: ${e.message}")
                e.printStackTrace()
            }
        })

        // Start tag update task
        tagManager.startUpdateTask()

        logger.info("SimpleDialog has been enabled!")
    }

    override fun onDisable() {
        // Save data
        playerDataManager.saveData()

        // Stop tasks
        tagManager.stopUpdateTask()

        logger.info("SimpleDialog has been disabled!")
    }

    fun reload() {
        configManager.loadConfig()
        configManager.loadDialogs()
        configManager.loadForms()
        playerDataManager.loadData()
        tagManager.updateAllTags()
        logger.info("SimpleDialog has been reloaded!")
    }
}