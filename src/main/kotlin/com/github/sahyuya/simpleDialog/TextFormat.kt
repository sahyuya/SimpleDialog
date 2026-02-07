package com.github.sahyuya.simpleDialog

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

// 設定ファイルのテキスト装飾を扱う共通ユーティリティ。
object TextFormat {
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    fun component(text: String): Component {
        return miniMessage.deserialize(text)
    }

    fun componentLines(lines: List<String>): Component {
        return component(lines.joinToString("\n"))
    }

    fun legacy(text: String): String {
        return legacySerializer.serialize(component(text))
    }

    fun legacyLines(lines: List<String>): String {
        return legacySerializer.serialize(componentLines(lines))
    }
}
