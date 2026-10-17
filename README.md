# RuruRules

RuruRulesは、サーバー初回参加時にプレイヤーに対してルール確認と同意を強制するPaper用プラグインです。同意するまでは、移動、チャット、ブロック操作などのアクションが制限されます。

## 概要 (Overview)
プレイヤーがサーバーに初めて参加した際、ウェブサイト上のルールをゲーム内の「本」として表示、またはチャットのリンクとして表示し、同意を求めます。同意データはUUIDベースでローカルファイルに保存されます。Folioにも対応しています。

## 機能 (Features)
- **行動制限**: ルールに同意するまでの間、以下の操作がブロックされます。
  - 移動（視点移動は可能）
  - ブロック破壊・設置
  - アイテムのドロップ・拾う操作
  - インベントリクリック
  - チャット送信
  - コマンド実行（`/rules`関連を除く）
  - エンティティやブロックとのインタラクト
- **Webからのルール取得**: 設定されたURLから自動的にHTMLを取得・解析し、Minecraft内の「本」の形式でプレイヤーに読ませることができます。
- **チャットフォールバック**: 「本」の表示が無効な場合や、Webからの取得に失敗した場合は、チャット上でURLと同意ボタンを通知します。
- **非同期処理**: Web通信などの重い処理は非同期で行われます。

## コマンド (Commands)
- `/rules` : ルールを再表示します。
- `/rules agree` : ルールに同意します（UIのボタンから自動実行されます）。
- `/rules reload` : [管理者専用] Webからルールの内容を再取得し、コンフィグをリロードします。
- `/rules reset <プレイヤー名>` : [管理者専用] 指定したプレイヤーの同意状態をリセットし、再度同意を求めます。

## パーミッション (Permissions)
- `rururules.admin` : 管理者コマンド（`reload`, `reset`）の実行権限。デフォルトは `op` です。

## 設定 (Configuration)
`config.yml` で以下の設定が可能です。

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

# HTMLから取得するCSSセレクタ (main content エリアを指定)
# 空にすると <body> 全体から取得
content-selector: "main, article, .content, #content"

# 操作をブロックした際にルールメッセージを再表示するか
show-message-on-block: true
```

## コード構成 (Code Structure/Architecture)
- **`RuruRulesPlugin`**: プラグインのメインクラス。初期化と各マネージャー・リスナーの登録を行います。
- **`RulesManager`**: プレイヤーの同意状態（`agreedPlayers`, `pendingPlayers`）を管理し、`agreed_players.txt` への非同期保存・読み込みを担当します。
- **`RulesCommand`**: `/rules` コマンドとそのサブコマンドの処理、タブ補完を実装しています。
- **`RulesListener`**: プレイヤーの様々なイベント（移動、チャット、ブロック操作など）を監視し、未同意プレイヤーのアクションをキャンセルします。
- **`RulesBookFetcher`**: Jsoupを使用して非同期にルールのHTMLを取得・パースし、テキストを分割してMinecraftの「本（Book）」コンポーネントを生成します。
