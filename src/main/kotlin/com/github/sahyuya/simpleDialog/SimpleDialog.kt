package com.github.sahyuya.simpleDialog

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

// プラグイン全体の状態を管理するメインクラス。
class SimpleDialog : JavaPlugin(), Listener {
    private lateinit var dialogConfig: DialogConfig
    private lateinit var playtimeTracker: PlaytimeTracker
    private lateinit var tagManager: TagManager
    private lateinit var dialogManager: DialogManager
    private var cleanupTask: BukkitTask? = null

    override fun onEnable() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        saveDefaultConfig()
        saveResource("dialog.yml", false)
        dialogConfig = DialogConfig(this)
        dialogConfig.reload()
        playtimeTracker = PlaytimeTracker(this)
        playtimeTracker.load()
        tagManager = TagManager(this, playtimeTracker)
        tagManager.load()
        dialogManager = DialogManager(dialogConfig, tagManager)
        dialogManager.setBedrockDialogs(BedrockDialogsFactory.tryCreate(this))

        server.pluginManager.registerEvents(this, this)
        registerPermissions()
        registerCommands()

        restartCleanupTask()
        for (player in server.onlinePlayers) {
            playtimeTracker.startSession(player, false)
            dialogManager.handleJoin(player)
        }
        tagManager.applyTagsToOnlinePlayers()
    }

    override fun onDisable() {
        cleanupTask?.cancel()
        for (player in server.onlinePlayers) {
            playtimeTracker.stopSession(player)
        }
    }

    fun reloadAll(regenerateResources: Boolean) {
        if (regenerateResources) {
            saveResource("config.yml", true)
            saveResource("dialog.yml", true)
        }
        reloadConfig()
        dialogConfig.reload()
        playtimeTracker.reloadSettings()
        tagManager.reloadSettings()
        restartCleanupTask()
        tagManager.applyTagsToOnlinePlayers()
    }

    private fun restartCleanupTask() {
        cleanupTask?.cancel()
        val intervalSeconds = config.getLong("tag-check-interval-seconds", 60)
        val ticks = intervalSeconds.coerceAtLeast(1) * 20L
        cleanupTask = server.scheduler.runTaskTimer(this, Runnable {
            // プレイ時間更新とタグ期限チェックを定期実行する。
            playtimeTracker.tick()
            tagManager.cleanupExpired()
        }, ticks, ticks)
    }

    private fun registerPermissions() {
        val manager = server.pluginManager
        if (manager.getPermission(SimpleDialogCommand.PERMISSION_ADMIN) == null) {
            manager.addPermission(
                Permission(SimpleDialogCommand.PERMISSION_ADMIN, PermissionDefault.OP)
            )
        }
    }

    private fun registerCommands() {
        val command = SimpleDialogCommand(this, dialogConfig, dialogManager, tagManager)
        registerCommand(SimpleDialogCommand.COMMAND_NAME, "SimpleDialog commands", command)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val isFirstJoin = !player.hasPlayedBefore() || player.firstPlayed == 0L
        playtimeTracker.startSession(player, isFirstJoin)
        dialogManager.handleJoin(player)
        tagManager.applyTagsIfActive(player)
        if (isFirstJoin && config.getBoolean("show-dialog-on-first-join", true)) {
            server.scheduler.runTaskLater(this, Runnable {
                if (player.isOnline) {
                    dialogManager.showWelcome(player)
                }
            }, 20L)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        dialogManager.handleQuit(player)
        playtimeTracker.stopSession(player)
    }
}
