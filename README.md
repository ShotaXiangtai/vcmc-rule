# RuruRules

サーバーへの初回参加時に、ルールの確認と同意を求める Folia 26.3 向けプラグインです。同意するまで、移動、チャット、ブロック操作などを制限します。

## 動作環境

| 項目 | バージョン |
|---|---|
| サーバー | Folia 26.3（`folia-api 26.3.build.6-alpha` でビルド） |
| Java | 25 以上 |

## 機能

- **行動制限**: ルールに同意するまで、次の操作をブロックします。
  - 移動（視点移動は可能）
  - ブロック破壊・設置
  - アイテムのドロップ・拾う操作
  - インベントリのクリック・ドラッグ
  - チャット送信（近距離チャットのプラグインにも流れません）
  - コマンド実行（`/rules` 関連を除く）
  - エンティティやブロックとのインタラクト
- **Web からのルール取得**: 設定した URL の HTML を解析し、ゲーム内の「本」として表示します。ページは定期的に取得し直すので、サイトを更新すれば本にも反映されます。
- **チャットフォールバック**: 本の表示が無効なときや取得に失敗したときは、チャットに URL と同意ボタンを表示します。取得に失敗しても前回の内容を使い続け、5 分後にやり直します。
- **再表示**: 制限された操作をすると、ルールを再表示します（3 秒に 1 回まで）。

## コマンド

- `/rules`（別名 `/rule`）: ルールを再表示します。
- `/rules agree`: ルールに同意します（本やチャットのボタンから実行されます）。
- `/rules reload`: [管理者] 設定を再読み込みし、ルールページを取得し直します。
- `/rules reset <プレイヤー名>`: [管理者] 同意状態をリセットし、再度同意を求めます。

## パーミッション

- `rururules.admin`: 管理者コマンド（`reload`, `reset`）の実行権限。デフォルトは `op` です。

## 設定

`config.yml` で次の設定ができます。

```yaml
# ルールページのURL
rules-url: "https://www.vcmc.jp/rules"

# 本としてルールを表示するか (false にするとチャットにURLを表示するだけ)
show-as-book: true

# 本のタイトル・著者
book-title: "vcmc.jp サーバールール"
book-author: "vcmc.jp"

# HTML取得タイムアウト (ミリ秒)
fetch-timeout-ms: 10000

# ルールページを取得し直す間隔 (分)。0 にすると起動時と /rules reload のときだけ取得します
refresh-interval-minutes: 60

# HTMLから取得するCSSセレクタ (main content エリアを指定)
# 空にすると <body> 全体から取得
content-selector: "main, article, .content, #content"

# 本に載せない要素のCSSセレクタ
exclude-selector: "nav, footer, script, style, noscript, form, button, .nav, .header, .footer, .sidebar, .site-header, .eyebrow, body > header"

# 操作をブロックした際にルールメッセージを再表示するか
show-message-on-block: true
```

`refresh-interval-minutes` と `exclude-selector` は、以前の `config.yml` に無くても既定値で動きます。

## コード構成

- `RuruRulesPlugin`: メインクラス。初期化と各クラス・コマンドの登録を行います。
- `RulesManager`: 同意状態を管理し、`agreed_players.txt` へ保存します。
- `RulesCommand`: `/rules` コマンドとタブ補完。
- `RulesListener`: 未同意プレイヤーの操作をキャンセルします。
- `RulesBookFetcher`: ルールページを非同期に取得して、本を作ります。
- `RulesPageParser`: HTML から本に載せる文章を取り出します。
- `BookLayout`: 文章を本のページに分けます。全角文字が多くても 1 ページに収まるよう、表示幅で判断します。

## ビルド

Java 25 が必要です。

```
mvn package
```

`target/ruru-rules-<バージョン>.jar` が生成されます。
