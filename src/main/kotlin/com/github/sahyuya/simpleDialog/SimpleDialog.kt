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
                // Register /simpledialog (sd) command
                val sdCommand = object : org.bukkit.command.Command(
                    "simpledialog",
                    "SimpleDialog メインコマンド（OP専用）",
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

                sdCommand.permission = "simpledialog.admin"
                server.commandMap.register("simpledialog", sdCommand)

                // /welcomeコマンドを登録（誰でも使用可能、maxPlaytime内のみ）
                val welcomeCommand = object : org.bukkit.command.Command(
                    "welcome",
                    "ウェルカム画面を自分に表示します",
                    "/welcome",
                    emptyList()
                ) {
                    override fun execute(
                        sender: org.bukkit.command.CommandSender,
                        commandLabel: String,
                        args: Array<out String>
                    ): Boolean {
                        if (sender !is org.bukkit.entity.Player) {
                            sender.sendMessage(net.kyori.adventure.text.Component.text("プレイヤーとして実行してください。")
                                .color(net.kyori.adventure.text.format.NamedTextColor.RED))
                            return true
                        }

                        // maxPlaytime内のプレイヤーのみ表示できる
                        if (!playerDataManager.shouldShowDialog(sender.uniqueId)) {
                            sender.sendMessage(net.kyori.adventure.text.Component.text("プレイ時間が上限を超えているため、案内を表示できません。")
                                .color(net.kyori.adventure.text.format.NamedTextColor.RED))
                            return true
                        }

                        // Java/Bedrock両対応でウェルカム画面を表示する
                        com.github.sahyuya.simpleDialog.util.DialogUtil.showWelcomeScreen(sender, this@SimpleDialog)
                        return true
                    }
                }

                server.commandMap.register("welcome", welcomeCommand)

                logger.info("コマンドの登録が完了しました！")
            } catch (e: Exception) {
                logger.warning("コマンドの登録に失敗しました: ${e.message}")
                e.printStackTrace()
            }
        })

        // タグ定期更新タスクを開始する
        tagManager.startUpdateTask()

        logger.info("SimpleDialogが有効になりました！")
    }

    override fun onDisable() {
        // データを保存する
        playerDataManager.saveData()

        // タスクを停止する
        tagManager.stopUpdateTask()

        logger.info("SimpleDialogが無効になりました！")
    }

    fun reload() {
        configManager.loadConfig()
        configManager.loadDialogs()
        configManager.loadForms()
        playerDataManager.loadData()
        tagManager.updateAllTags()
        logger.info("SimpleDialogをリロードしました！")
    }
}