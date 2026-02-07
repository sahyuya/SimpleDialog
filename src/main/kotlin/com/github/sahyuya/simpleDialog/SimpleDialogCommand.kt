package com.github.sahyuya.simpleDialog

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

// Paperコマンドの実装。
class SimpleDialogCommand(
    private val plugin: JavaPlugin,
    private val dialogConfig: DialogConfig,
    private val dialogManager: DialogManager,
    private val tagManager: TagManager
) : BasicCommand {
    // サブコマンド処理を集約する。
    override fun execute(source: CommandSourceStack, args: Array<out String>) {
        val sender = source.sender
        if (args.isEmpty()) {
            sender.sendMessage("使い方: /$COMMAND_NAME <open|on|off|toggle|cleartag|reload>")
            return
        }
        when (args[0].lowercase()) {
            "open" -> {
                handleOpen(sender, args)
            }
            "on" -> {
                if (!isAdmin(sender)) {
                    sender.sendMessage("このコマンドを実行する権限がありません。")
                    return
                }
                plugin.config.set("show-dialog-on-first-join", true)
                plugin.saveConfig()
                sender.sendMessage("初参加ダイアログを有効化しました。")
            }
            "off" -> {
                if (!isAdmin(sender)) {
                    sender.sendMessage("このコマンドを実行する権限がありません。")
                    return
                }
                plugin.config.set("show-dialog-on-first-join", false)
                plugin.saveConfig()
                sender.sendMessage("初参加ダイアログを無効化しました。")
            }
            "toggle" -> {
                if (!isAdmin(sender)) {
                    sender.sendMessage("このコマンドを実行する権限がありません。")
                    return
                }
                val next = !plugin.config.getBoolean("show-dialog-on-first-join", true)
                plugin.config.set("show-dialog-on-first-join", next)
                plugin.saveConfig()
                sender.sendMessage("初参加ダイアログを${if (next) "有効" else "無効"}にしました。")
            }
            "cleartag" -> {
                if (!isAdmin(sender)) {
                    sender.sendMessage("このコマンドを実行する権限がありません。")
                    return
                }
                if (args.size < 2) {
                    sender.sendMessage("使い方: /$COMMAND_NAME cleartag <player>")
                    return
                }
                val targetName = args[1]
                val target = Bukkit.getPlayerExact(targetName)
                    ?: Bukkit.getOfflinePlayer(targetName)
                tagManager.clearTags(target.uniqueId)
                sender.sendMessage("${target.name ?: target.uniqueId} のタグを解除しました。")
            }
            "reload" -> {
                if (!isAdmin(sender)) {
                    sender.sendMessage("このコマンドを実行する権限がありません。")
                    return
                }
                if (plugin is SimpleDialog) {
                    plugin.reloadAll(regenerateResources = true)
                } else {
                    plugin.reloadConfig()
                    dialogConfig.reload()
                    tagManager.reloadSettings()
                    tagManager.applyTagsToOnlinePlayers()
                }
                sender.sendMessage("SimpleDialogの設定とダイアログを再読み込みしました。")
            }
            else -> {
                sender.sendMessage("使い方: /$COMMAND_NAME <open|on|off|toggle|cleartag|reload>")
            }
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<out String>): Collection<String> {
        val sender = source.sender
        val isAdmin = isAdmin(sender)
        val subcommands = if (isAdmin) {
            listOf("open", "on", "off", "toggle", "cleartag", "reload")
        } else {
            listOf("open")
        }
        if (args.isEmpty()) {
            return subcommands
        }
        if (args.size == 1) {
            return subcommands.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("cleartag", ignoreCase = true) && isAdmin) {
            return Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("open", ignoreCase = true) && isAdmin) {
            return Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }

    override fun canUse(sender: CommandSender): Boolean {
        return true
    }

    private fun handleOpen(sender: CommandSender, args: Array<out String>) {
        if (args.size >= 2) {
            if (!isAdmin(sender)) {
                sender.sendMessage("このコマンドを実行する権限がありません。")
                return
            }
            val target = Bukkit.getPlayerExact(args[1])
            if (target == null) {
                sender.sendMessage("プレイヤーが見つかりません: ${args[1]}")
                return
            }
            // OPは誰にでも再表示できる。
            dialogManager.resetSession(target)
            dialogManager.showWelcome(target)
            sender.sendMessage("${target.name} にダイアログを開きました。")
            return
        }
        if (sender !is Player) {
            sender.sendMessage("使い方: /$COMMAND_NAME open <player>")
            return
        }
        if (!isAdmin(sender) && !tagManager.isWithinPlaytime(sender)) {
            sender.sendMessage("許可されたプレイ時間内のみ再表示できます。")
            return
        }
        dialogManager.resetSession(sender)
        dialogManager.showWelcome(sender)
    }

    private fun isAdmin(sender: CommandSender): Boolean {
        return sender.isOp || sender.hasPermission(PERMISSION_ADMIN)
    }

    companion object {
        const val COMMAND_NAME = "simpledialog"
        const val PERMISSION_ADMIN = "simpledialog.admin"
    }
}
