# SimpleDialog

PaperMCのDialog APIとGeyserのCumulus Formsを使用した、サーバー初参加者向けの案内プラグインです。

## 機能

- **Java版プレイヤーにはDialog API（書籍形式）、Bedrock版プレイヤーにはCumulus Formsを使用した案内画面の表示**
- **DynamicProfileのplayTimeが0かつplayerdataにないプレイヤーに自動表示**
- プレイヤーの目的（建築/観光）とジャンルタグの設定
- ネームタグ下にタグを表示
- DynamicProfileのプレイ時間に基づいてタグを管理
- max playtimeを超えたプレイヤーはplayerdataから自動削除
- 設定ファイルで各種カスタマイズ可能（色、サイズ、フォーマット）

## 必要環境

- Minecraft 1.21.8
- Purpur/Paper サーバー
- DynamicProfile プラグイン (プレイ時間管理用)
- Geyser (Bedrock版対応の場合)
- Floodgate (Bedrock版対応の場合)

## ビルド方法

```bash
./gradlew shadowJar
```

ビルドされたjarファイルは `build/libs` に生成されます。

## コマンド

- `/simpledialog reload` (または `/sd reload`) - 設定をリロード
- `/simpledialog enable` (または `/sd enable`) - 初回参加時のダイアログ表示を有効化
- `/simpledialog disable` (または `/sd disable`) - 初回参加時のダイアログ表示を無効化
- `/simpledialog cleartag <player>` (または `/sd cleartag <player>`) - プレイヤーのタグをクリア
- `/simpledialog show [player]` (または `/sd show [player]`) - ウェルカム画面を表示
    - 引数なし: 自分に表示（max playtime内の新規プレイヤーのみ再表示可能）
    - 引数あり: 指定したプレイヤーに表示（OP専用）
- `/simpledialog regenerate` (または `/sd regenerate`) - すべての設定ファイルを再生成してリロード

## 設定ファイル

### config.yml

基本設定を管理します。

- `show-on-first-join`: 初回参加時にダイアログを表示するか
- `tag.max-playtime`: タグを表示する最大プレイ時間（分単位、DynamicProfileと同じ）
- `tag.format`: タグの表示形式
- `tag.purpose-colors`: 目的タグの色設定
- `tag.genre-color`: ジャンルタグの色設定
- `cleanup-on-tag-removal`: タグクリア時にデータも削除するか

### dialogs_ja.yml / dialogs_en.yml

Java版プレイヤー向けのDialog設定ファイルです。
各画面のタイトル、テキスト、ボタンをカスタマイズできます。

### forms_ja.yml / forms_en.yml

Bedrock版プレイヤー向けのForms設定ファイルです。
各画面のタイトル、内容、ボタンをカスタマイズできます。

## Dialog APIの仕様について

### ボタンの配置

**重要:** Paper 1.21.8のDialog APIでは、**ボタンは常に画面の下部に表示されます**。

YAMLファイルで`sections`の順序を変更しても、以下のように表示されます：
- **テキスト**: 上部に表示（sectionsの順序通り）
- **ボタン**: 常に下部に表示（YAMLで最初に書いても下に表示される）

これはMinecraftのDialog APIの仕様です。

### sections配列の順序

`sections`配列は以下のように機能します：
- `type: "text"` - テキストを**上から順に**表示
- `type: "buttons"` - ボタンを収集（表示位置は常に下部）
- `type: "checkboxes"` - チェックボックス入力フィールドを追加

```yaml
sections:
  - type: "buttons"  # ← 最初に書いても...
    items: [...]
  - type: "text"     # ← テキストが上に表示される
    lines: [...]
  - type: "buttons"  # ← 2つ目のボタングループ
    items: [...]
```

**表示結果:**
```
[テキスト]
[ボタン1][ボタン2][ボタン3]
```

## カスタマイズ方法

### テキストの変更

各YAML設定ファイルの `text` または `content` セクションを編集することで、
表示されるテキストを変更できます。

### テキストの色とサイズ

Minecraftのカラーコードと装飾コードを使用できます：

**色コード:**
- `&0` - 黒
- `&1` - 濃い青
- `&2` - 濃い緑
- `&3` - 濃い水色
- `&4` - 濃い赤
- `&5` - 濃い紫
- `&6` - 金色
- `&7` - 灰色
- `&8` - 濃い灰色
- `&9` - 青
- `&a` - 緑
- `&b` - 水色
- `&c` - 赤
- `&d` - ピンク
- `&e` - 黄色
- `&f` - 白

**装飾コード（太字で大きく見える）:**
- `&l` - **太字** (大きく見える)
- `&o` - *斜体*
- `&n` - 下線
- `&m` - 取り消し線
- `&k` - 難読化
- `&r` - リセット

**使用例:**
```yaml
title: "&6&lようこそ！"  # 金色で太字のタイトル（大きく見える）

text:
  - "&e&lおやさいサーバーへようこそ！"  # 黄色で太字
  - "&7こちらは建築に特化したサーバーです。"  # 灰色の通常サイズ
  - "&c&n重要な注意事項"  # 赤色で下線

buttons:
  building:
    text: "&a&l建築したい！"  # 緑色で太字のボタン
```

**サイズについて:**
Minecraftには厳密な「文字サイズ」の概念はありませんが、`&l`（太字）を使うと文字が太く大きく見えます。
タイトルには必ず`&l`を付けることをおすすめします。

### ボタンの変更

`buttons` セクションでボタンのテキストや動作を設定できます。
ボタンのテキストにもカラーコードを使用できます：

```yaml
buttons:
  ok:
    text: "&b&lOK"  # 水色で太字のOKボタン
```

### タグの色

`config.yml` の `tag.purpose-colors` と `tag.genre-color` で色を変更できます。
Minecraft のカラーコード（`&a`, `&b` など）を使用します。

### プレイ時間の制限

`config.yml` の `tag.max-playtime` で、タグを表示する最大プレイ時間を分単位で設定できます。
DynamicProfileと同じ単位を使用しています。
デフォルトは180分（3時間）です。

## ライセンス

このプロジェクトはMITライセンスの下で公開されています。