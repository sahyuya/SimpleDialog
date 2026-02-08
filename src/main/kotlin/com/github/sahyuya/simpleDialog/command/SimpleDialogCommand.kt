package com.github.sahyuya.simpleDialog.command

import com.github.sahyuya.simpleDialog.SimpleDialog
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class SimpleDialogCommand(private val plugin: SimpleDialog) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("simpledialog.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                .color(NamedTextColor.RED))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "reload" -> {
                plugin.reload()
                sender.sendMessage(Component.text("SimpleDialog has been reloaded!")
                    .color(NamedTextColor.GREEN))
            }

            "enable" -> {
                plugin.configManager.loadConfig()
                plugin.config.set("show-on-first-join", true)
                plugin.saveConfig()
                plugin.configManager.loadConfig()
                sender.sendMessage(Component.text("First join dialog has been enabled!")
                    .color(NamedTextColor.GREEN))
            }

            "disable" -> {
                plugin.configManager.loadConfig()
                plugin.config.set("show-on-first-join", false)
                plugin.saveConfig()
                plugin.configManager.loadConfig()
                sender.sendMessage(Component.text("First join dialog has been disabled!")
                    .color(NamedTextColor.GREEN))
            }

            "cleartag" -> {
                if (args.size < 2) {
                    sender.sendMessage(Component.text("Usage: /simpledialog cleartag <player>")
                        .color(NamedTextColor.RED))
                    return true
                }

                val targetName = args[1]
                val target = Bukkit.getPlayerExact(targetName)

                if (target == null) {
                    sender.sendMessage(Component.text("Player not found: $targetName")
                        .color(NamedTextColor.RED))
                    return true
                }

                plugin.tagManager.clearPlayerTag(target)
                sender.sendMessage(Component.text("Cleared tag for ${target.name}")
                    .color(NamedTextColor.GREEN))
            }

            "show" -> {
                if (sender !is Player && args.size < 2) {
                    sender.sendMessage(Component.text("Usage: /simpledialog show <player>")
                        .color(NamedTextColor.RED))
                    return true
                }

                val targetPlayer = if (args.size >= 2) {
                    // Show to specified player (OP only)
                    val targetName = args[1]
                    Bukkit.getPlayerExact(targetName)
                } else {
                    // Show to self
                    sender as Player
                }

                if (targetPlayer == null) {
                    sender.sendMessage(Component.text("Player not found.")
                        .color(NamedTextColor.RED))
                    return true
                }

                // Check if player can see dialog (only for self-show)
                if (args.size < 2 && sender is Player) {
                    if (!plugin.playerDataManager.shouldShowDialog(sender.uniqueId)) {
                        sender.sendMessage(Component.text("You have exceeded the maximum playtime for viewing this dialog.")
                            .color(NamedTextColor.RED))
                        return true
                    }
                }

                // Check if player is Bedrock safely
                val isBedrockPlayer = try {
                    val floodgateClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                    val getInstance = floodgateClass.getMethod("getInstance")
                    val api = getInstance.invoke(null)
                    val isFloodgatePlayer = api.javaClass.getMethod("isFloodgatePlayer", java.util.UUID::class.java)
                    isFloodgatePlayer.invoke(api, targetPlayer.uniqueId) as Boolean
                } catch (e: Exception) {
                    false
                }

                if (isBedrockPlayer) {
                    plugin.formManager.showWelcomeForm(targetPlayer)
                } else {
                    plugin.dialogManager.showWelcomeDialog(targetPlayer)
                }

                if (targetPlayer != sender) {
                    sender.sendMessage(Component.text("Showing welcome dialog to ${targetPlayer.name}")
                        .color(NamedTextColor.GREEN))
                }
            }

            "regenerate" -> {
                plugin.configManager.regenerateFiles()
                plugin.reload()
                sender.sendMessage(Component.text("All configuration files have been regenerated and reloaded!")
                    .color(NamedTextColor.GREEN))
            }

            else -> {
                sender.sendMessage(Component.text("SimpleDialog Commands:")
                    .color(NamedTextColor.YELLOW))
                sender.sendMessage(Component.text("  /sd reload - Reload configuration")
                    .color(NamedTextColor.GRAY))
                sender.sendMessage(Component.text("  /sd enable - Enable first join dialog")
                    .color(NamedTextColor.GRAY))
                sender.sendMessage(Component.text("  /sd disable - Disable first join dialog")
                    .color(NamedTextColor.GRAY))
                sender.sendMessage(Component.text("  /sd cleartag <player> - Clear player's tag")
                    .color(NamedTextColor.GRAY))
                sender.sendMessage(Component.text("  /sd show [player] - Show welcome dialog")
                    .color(NamedTextColor.GRAY))
                sender.sendMessage(Component.text("  /sd regenerate - Regenerate all config files")
                    .color(NamedTextColor.GRAY))
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("reload", "enable", "disable", "cleartag", "show", "regenerate")
                .filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2) {
            when (args[0].lowercase()) {
                "cleartag", "show" -> {
                    return Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                }
            }
        }

        return emptyList()
    }
}