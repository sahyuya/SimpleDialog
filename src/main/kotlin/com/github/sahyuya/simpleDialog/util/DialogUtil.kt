package com.github.sahyuya.simpleDialog.util

import com.github.sahyuya.simpleDialog.SimpleDialog
import org.bukkit.entity.Player

// プレイヤーへのDialog/Form表示に関する共通ユーティリティ
object DialogUtil {

    // Geyser-SpigotがインストールされているかのキャッシュフラグはRuntime判定で十分

    // Bedrockプレイヤーかどうかをリフレクションで安全に判定する
    fun isBedrockPlayer(player: Player, plugin: SimpleDialog): Boolean {
        if (plugin.server.pluginManager.getPlugin("Geyser-Spigot") == null) {
            return false
        }
        return try {
            val geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
            val apiMethod = geyserApiClass.getMethod("api")
            val apiInstance = apiMethod.invoke(null)
            val isBedrockMethod = apiInstance.javaClass
                .getMethod("isBedrockPlayer", java.util.UUID::class.java)
            isBedrockMethod.invoke(apiInstance, player.uniqueId) as Boolean
        } catch (e: Exception) {
            plugin.logger.warning("Bedrockプレイヤー判定中にエラーが発生しました: ${e.message}")
            false
        }
    }

    // プレイヤーの種別に応じてDialog（Java）またはForm（Bedrock）を表示する
    fun showWelcomeScreen(player: Player, plugin: SimpleDialog) {
        if (isBedrockPlayer(player, plugin)) {
            plugin.formManager.showWelcomeForm(player)
        } else {
            plugin.dialogManager.showWelcomeDialog(player)
        }
    }
}