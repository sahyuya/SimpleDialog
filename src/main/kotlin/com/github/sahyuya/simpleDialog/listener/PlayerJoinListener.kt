package com.github.sahyuya.simpleDialog.listener

import com.github.sahyuya.simpleDialog.SimpleDialog
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.scheduler.BukkitRunnable

// プレイヤー参加時のイベントを処理するリスナー
class PlayerJoinListener(private val plugin: SimpleDialog) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // ロード完了まで少し待ってから処理する
        object : BukkitRunnable() {
            override fun run() {
                handlePlayerJoin(player)
            }
        }.runTaskLater(plugin, 20L) // 1秒後に実行
    }

    private fun handlePlayerJoin(player: Player) {
        val uuid = player.uniqueId
        val playtime = plugin.playerDataManager.getPlaytime(uuid)
        val maxPlaytime = plugin.configManager.maxPlaytime

        plugin.logger.info("${player.name} のプレイ時間: ${playtime}分 / 上限: ${maxPlaytime}分")

        // maxPlaytimeを超えているプレイヤーのデータを削除する
        // DynamicProfileはサーバー退出時に書き込まれるため、ログイン時に判定する
        if (playtime > maxPlaytime) {
            if (plugin.playerDataManager.hasPlayerData(uuid)) {
                plugin.logger.info("${player.name} のプレイ時間が上限を超えたため、データを削除します (${playtime}分 > ${maxPlaytime}分)")
                plugin.playerDataManager.clearTags(uuid)
                plugin.tagManager.forceRemoveTag(player)
            }
            // maxPlaytime超過後はDialogやタグの更新をしない
            return
        }

        // 初回参加かどうかを判定してDialogを表示する
        val isNewPlayer = plugin.playerDataManager.isNewPlayer(uuid)

        if (isNewPlayer && plugin.configManager.showOnFirstJoin) {
            showWelcomeScreen(player)
        } else {
            // 既存プレイヤーはタグを更新する
            plugin.tagManager.updateTag(player)
        }
    }

    fun showWelcomeScreen(player: Player) {
        // Geyser-Spigotがインストールされている場合のみBedrockプレイヤーを判定する
        val isBedrockPlayer = try {
            if (plugin.server.pluginManager.getPlugin("Geyser-Spigot") != null) {
                // ClassNotFoundExceptionを避けるためリフレクションを使用
                val geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
                val apiMethod = geyserApiClass.getMethod("api")
                val apiInstance = apiMethod.invoke(null)
                val isBedrockMethod = apiInstance.javaClass.getMethod("isBedrockPlayer", java.util.UUID::class.java)
                isBedrockMethod.invoke(apiInstance, player.uniqueId) as Boolean
            } else {
                false
            }
        } catch (e: Exception) {
            plugin.logger.warning("Bedrockプレイヤー判定中にエラーが発生しました: ${e.message}")
            false
        }

        if (isBedrockPlayer) {
            // Bedrockプレイヤー向けのCumulusフォームを表示
            plugin.formManager.showWelcomeForm(player)
        } else {
            // Javaプレイヤー向けのDialogを表示
            plugin.dialogManager.showWelcomeDialog(player)
        }
    }
}