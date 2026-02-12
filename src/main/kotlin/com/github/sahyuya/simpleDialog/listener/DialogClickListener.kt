package com.github.sahyuya.simpleDialog.listener

import com.github.sahyuya.simpleDialog.SimpleDialog
import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.event.player.PlayerCustomClickEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class DialogClickListener(private val plugin: SimpleDialog) : Listener {

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        val identifier = event.identifier

        // Check if this is a SimpleDialog action
        if (identifier.namespace() != "simpledialog") {
            return
        }

        val player = getPlayerFromEvent(event) ?: return

        plugin.logger.info("Dialog click: ${identifier.value()} from ${player.name}")

        // Always pass both identifier and responseView to DialogManager
        plugin.dialogManager.handleDialogAction(player, identifier, event.dialogResponseView)
    }

    private fun getPlayerFromEvent(event: PlayerCustomClickEvent): Player? {
        val connection = event.commonConnection

        return if (connection is PlayerGameConnection) {
            connection.player
        } else {
            plugin.logger.warning("Cannot get player from connection: ${connection::class.java.simpleName}")
            null
        }
    }
}