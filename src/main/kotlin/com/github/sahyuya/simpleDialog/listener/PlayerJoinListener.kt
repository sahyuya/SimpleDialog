package com.github.sahyuya.simpleDialog.listener

import com.github.sahyuya.simpleDialog.SimpleDialog
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.scheduler.BukkitRunnable

class PlayerJoinListener(private val plugin: SimpleDialog) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Check if player should see the dialog
        val shouldShow = plugin.playerDataManager.isNewPlayer(player.uniqueId)

        if (shouldShow && plugin.configManager.showOnFirstJoin) {
            // Delay to ensure player is fully loaded
            object : BukkitRunnable() {
                override fun run() {
                    showWelcomeScreen(player)
                }
            }.runTaskLater(plugin, 20L) // 1 second delay
        } else {
            // Update tag for existing players
            plugin.tagManager.updateTag(player)
        }

        // Clean up expired players periodically
        object : BukkitRunnable() {
            override fun run() {
                plugin.playerDataManager.cleanupExpiredPlayers()
            }
        }.runTaskLater(plugin, 40L) // 2 seconds delay
    }

    private fun showWelcomeScreen(player: org.bukkit.entity.Player) {
        // Check if player is Bedrock or Java safely
        val isBedrockPlayer = try {
            val floodgateClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
            val getInstance = floodgateClass.getMethod("getInstance")
            val api = getInstance.invoke(null)
            val isFloodgatePlayer = api.javaClass.getMethod("isFloodgatePlayer", java.util.UUID::class.java)
            isFloodgatePlayer.invoke(api, player.uniqueId) as Boolean
        } catch (e: Exception) {
            false
        }

        if (isBedrockPlayer) {
            // Show Cumulus Form for Bedrock players
            plugin.formManager.showWelcomeForm(player)
        } else {
            // Show Dialog for Java players
            plugin.dialogManager.showWelcomeDialog(player)
        }
    }
}