package com.github.sahyuya.simpleDialog.command

import com.github.sahyuya.simpleDialog.SimpleDialog
import com.github.sahyuya.simpleDialog.util.DialogUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

// /sd コマンドのハンドラー（OP専用）
class SimpleDialogCommand(private val plugin: SimpleDialog) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        // /sdコマンド全体をOP専用にする
        if (!sender.isOp) {
            sender.sendMessage(Component.text("このコマンドはOP専用です。")
                .color(NamedTextColor.RED))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {

            // 設定をリロードする
            "reload" -> {
                plugin.reload()
                sender.sendMessage(Component.text("SimpleDialogをリロードしました！")
                    .color(NamedTextColor.GREEN))
            }

            // 初回参加時のDialog表示を有効化する
            "enable" -> {
                plugin.config.set("show-on-first-join", true)
                plugin.saveConfig()
                plugin.configManager.loadConfig()
                sender.sendMessage(Component.text("初回参加Dialogを有効化しました！")
                    .color(NamedTextColor.GREEN))
            }

            // 初回参加時のDialog表示を無効化する
            "disable" -> {
                plugin.config.set("show-on-first-join", false)
                plugin.saveConfig()
                plugin.configManager.loadConfig()
                sender.sendMessage(Component.text("初回参加Dialogを無効化しました！")
                    .color(NamedTextColor.GREEN))
            }

            // プレイヤーのタグを完全消去する
            "cleartag" -> {
                if (args.size < 2) {
                    sender.sendMessage(Component.text("使い方: /sd cleartag <プレイヤー名>")
                        .color(NamedTextColor.RED))
                    return true
                }
                val targetName = args[1]
                val target = Bukkit.getPlayerExact(targetName)
                if (target == null) {
                    sender.sendMessage(Component.text("プレイヤーが見つかりません: $targetName")
                        .color(NamedTextColor.RED))
                    return true
                }
                // タグをスコアボードから完全消去し、playerDataも削除する
                plugin.tagManager.clearPlayerTag(target)
                sender.sendMessage(Component.text("${target.name} のタグを削除しました。")
                    .color(NamedTextColor.GREEN))
            }

            // ウェルカム画面を表示する（OP専用）
            // 引数なし: 自分に表示
            // 引数あり: 指定プレイヤーに表示
            "show" -> {
                if (args.size >= 2) {
                    // 他のプレイヤーに表示する
                    val targetName = args[1]
                    val targetPlayer = Bukkit.getPlayerExact(targetName)
                    if (targetPlayer == null) {
                        sender.sendMessage(Component.text("プレイヤーが見つかりません: $targetName")
                            .color(NamedTextColor.RED))
                        return true
                    }
                    // Java/Bedrock両対応で表示する
                    DialogUtil.showWelcomeScreen(targetPlayer, plugin)
                    sender.sendMessage(Component.text("${targetPlayer.name} にウェルカム画面を表示しました。")
                        .color(NamedTextColor.GREEN))
                } else {
                    // 自分に表示する
                    if (sender !is Player) {
                        sender.sendMessage(Component.text("プレイヤーとして実行してください。")
                            .color(NamedTextColor.RED))
                        return true
                    }
                    // Java/Bedrock両対応で表示する
                    DialogUtil.showWelcomeScreen(sender, plugin)
                }
            }

            // 設定ファイルを再生成する
            // 引数なし: 全ファイルを再生成
            // 引数あり: 指定したファイルのみ再生成（config / dialogs_ja / dialogs_en）
            "regenerate" -> {
                when (args.getOrNull(1)?.lowercase()) {
                    "config" -> {
                        plugin.configManager.regenerateFile("config")
                        plugin.configManager.loadConfig()
                        sender.sendMessage(Component.text("config.yml を再生成しました！")
                            .color(NamedTextColor.GREEN))
                    }
                    "dialogs_ja" -> {
                        plugin.configManager.regenerateFile("dialogs_ja")
                        plugin.configManager.loadDialogs()
                        sender.sendMessage(Component.text("dialogs_ja.yml を再生成しました！")
                            .color(NamedTextColor.GREEN))
                    }
                    "dialogs_en" -> {
                        plugin.configManager.regenerateFile("dialogs_en")
                        plugin.configManager.loadDialogs()
                        sender.sendMessage(Component.text("dialogs_en.yml を再生成しました！")
                            .color(NamedTextColor.GREEN))
                    }
                    null -> {
                        // 全ファイルを再生成してリロード
                        plugin.configManager.regenerateFiles()
                        plugin.reload()
                        sender.sendMessage(Component.text("全設定ファイルを再生成しました！")
                            .color(NamedTextColor.GREEN))
                    }
                    else -> {
                        sender.sendMessage(Component.text("指定できるファイル名: config / dialogs_ja / dialogs_en")
                            .color(NamedTextColor.RED))
                    }
                }
            }

            // ヘルプを表示する
            else -> {
                sender.sendMessage(Component.text("=== SimpleDialog コマンド一覧 ===").color(NamedTextColor.YELLOW))
                sender.sendMessage(Component.text("  /sd reload").color(NamedTextColor.AQUA)
                    .append(Component.text(" - 設定をリロード").color(NamedTextColor.GRAY)))
                sender.sendMessage(Component.text("  /sd enable").color(NamedTextColor.AQUA)
                    .append(Component.text(" - 初回参加Dialogを有効化").color(NamedTextColor.GRAY)))
                sender.sendMessage(Component.text("  /sd disable").color(NamedTextColor.AQUA)
                    .append(Component.text(" - 初回参加Dialogを無効化").color(NamedTextColor.GRAY)))
                sender.sendMessage(Component.text("  /sd cleartag <プレイヤー>").color(NamedTextColor.AQUA)
                    .append(Component.text(" - プレイヤーのタグを完全消去").color(NamedTextColor.GRAY)))
                sender.sendMessage(Component.text("  /sd show [プレイヤー]").color(NamedTextColor.AQUA)
                    .append(Component.text(" - ウェルカム画面を表示").color(NamedTextColor.GRAY)))
                sender.sendMessage(Component.text("  /sd regenerate [config|dialogs_ja|dialogs_en]").color(NamedTextColor.AQUA)
                    .append(Component.text(" - 設定ファイルを再生成").color(NamedTextColor.GRAY)))
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
        if (!sender.isOp) return emptyList()

        return when {
            args.size == 1 -> {
                listOf("reload", "enable", "disable", "cleartag", "show", "regenerate")
                    .filter { it.startsWith(args[0].lowercase()) }
            }
            args.size == 2 -> {
                when (args[0].lowercase()) {
                    "cleartag", "show" -> {
                        Bukkit.getOnlinePlayers()
                            .map { it.name }
                            .filter { it.startsWith(args[1], ignoreCase = true) }
                    }
                    "regenerate" -> {
                        listOf("config", "dialogs_ja", "dialogs_en")
                            .filter { it.startsWith(args[1].lowercase()) }
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}