package com.github.sahyuya.simpleDialog.tag

import com.github.sahyuya.simpleDialog.SimpleDialog
import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective

// プレイヤーのネームタグ下にタグを表示するクラス
class TagManager(private val plugin: SimpleDialog) {

    private var updateTask: BukkitTask? = null
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    companion object {
        // スコアボードのオブジェクト名
        private const val OBJECTIVE_NAME = "sd_tags"
    }

    // 定期更新タスクを開始する
    fun startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updateAllTags()
            checkAndRemoveExpiredTags()
        }, 0L, 20L * 60L) // 1分ごとに更新
    }

    // 定期更新タスクを停止する
    fun stopUpdateTask() {
        updateTask?.cancel()
        updateTask = null
    }

    // オンラインの全プレイヤーのタグを更新する
    fun updateAllTags() {
        Bukkit.getOnlinePlayers().forEach { updateTag(it) }
    }

    // 指定プレイヤーのタグを更新する
    fun updateTag(player: Player) {
        val playtime = plugin.playerDataManager.getPlaytime(player.uniqueId)
        val maxPlaytime = plugin.configManager.maxPlaytime

        // maxPlaytimeを超えている場合はタグを削除
        if (playtime > maxPlaytime) {
            forceRemoveTag(player)
            return
        }

        val data = plugin.playerDataManager.getPlayerDataOrNull(player.uniqueId)

        // 目的もジャンルも未設定ならタグを削除
        if (data == null || (data.purpose == null && data.genres.isEmpty())) {
            forceRemoveTag(player)
            return
        }

        val tagComponent = buildTagComponent(data.purpose, data.genres)
        setTag(player, tagComponent)
    }

    // 目的とジャンルからタグのComponentを組み立てる
    private fun buildTagComponent(purpose: String?, genres: List<String>): Component {
        var result = Component.empty()
        var hasContent = false

        if (purpose != null) {
            val text = when (purpose) {
                "building" -> "${plugin.configManager.buildingColor}建築目的"
                "sightseeing" -> "${plugin.configManager.sightseeingColor}観光目的"
                else -> ""
            }
            if (text.isNotEmpty()) {
                result = result.append(legacySerializer.deserialize(text))
                hasContent = true
            }
        }

        genres.forEach { genre ->
            if (hasContent) result = result.append(Component.text(" "))
            result = result.append(legacySerializer.deserialize("${plugin.configManager.genreColor}#$genre"))
            hasContent = true
        }

        return result
    }

    // スコアボードのBELOW_NAMEを使ってタグをネームタグ下に表示する
    private fun setTag(player: Player, tagComponent: Component) {
        val objective = ensureObjective() ?: return
        val score = objective.getScore(player)
        score.score = 0
        score.numberFormat(NumberFormat.fixed(tagComponent))
    }

    // スコアのスコアをリセットしてタグを非表示にする
    fun forceRemoveTag(player: Player) {
        val manager = Bukkit.getScoreboardManager() ?: return
        val scoreboard = manager.mainScoreboard
        val objective = scoreboard.getObjective(OBJECTIVE_NAME) ?: return
        val score = objective.getScore(player)
        if (score.isScoreSet) {
            score.resetScore()
        }
    }

    // オフラインプレイヤーのタグもリセットする
    private fun forceRemoveTagOffline(offlinePlayer: OfflinePlayer) {
        val manager = Bukkit.getScoreboardManager() ?: return
        val scoreboard = manager.mainScoreboard
        val objective = scoreboard.getObjective(OBJECTIVE_NAME) ?: return
        val score = objective.getScore(offlinePlayer)
        if (score.isScoreSet) {
            score.resetScore()
        }
    }

    // BELOW_NAMEのObjectiveを取得または新規作成する
    private fun ensureObjective(): Objective? {
        val manager = Bukkit.getScoreboardManager() ?: return null
        val scoreboard = manager.mainScoreboard

        var objective = scoreboard.getObjective(OBJECTIVE_NAME)
        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                OBJECTIVE_NAME,
                Criteria.DUMMY,
                Component.empty()
            )
        }

        if (objective.displaySlot != DisplaySlot.BELOW_NAME) {
            val existing = scoreboard.getObjective(DisplaySlot.BELOW_NAME)
            if (existing != null && existing.name != OBJECTIVE_NAME) {
                plugin.logger.warning("BELOW_NAMEの表示が他のプラグインと競合しています: ${existing.name}")
            }
            objective.displaySlot = DisplaySlot.BELOW_NAME
        }

        return objective
    }

    // タグを完全消去し、playerDataも削除する
    fun clearPlayerTag(player: Player) {
        plugin.playerDataManager.clearTags(player.uniqueId)
        forceRemoveTag(player)
    }

    // maxPlaytimeを超えたプレイヤーのタグを定期的に削除する
    private fun checkAndRemoveExpiredTags() {
        val maxPlaytime = plugin.configManager.maxPlaytime

        Bukkit.getOnlinePlayers().forEach { player ->
            val playtime = plugin.playerDataManager.getPlaytime(player.uniqueId)
            if (playtime > maxPlaytime) {
                plugin.logger.info("${player.name} のプレイ時間が上限を超えたため、タグを削除します")
                clearPlayerTag(player)
            }
        }

        // playerDataにあるオフラインプレイヤーも確認
        plugin.playerDataManager.cleanupExpiredPlayers()
    }
}