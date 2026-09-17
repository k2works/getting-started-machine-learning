# 運用

開発環境構築・デプロイ・運用に関するドキュメントです。

## ドキュメント一覧

### 環境セットアップ

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| アプリケーション開発環境セットアップ手順書 | ローカルアプリケーション開発環境の構築手順 | 未作成 |
| 開発環境セットアップ手順書 | 開発環境のインフラ構築手順 | 未作成 |
| AWS ステージング環境セットアップ手順書 | ステージング環境の構築手順 | 未作成 |
| AWS プロダクション環境セットアップ手順書 | 本番環境の構築手順 | 未作成 |

### 運用コマンド

#### OKF（知識バンドル）

`docs/` は OKF v0.2 の知識バンドルです（[OKF 導入ガイド](../reference/OKF導入ガイド_V0.2.md)）。移行・検証は `migrating-okf` スキル、日常の運用は以下の Gulp タスクを使います。

| コマンド | 概要 |
| :--- | :--- |
| `gulp okf:check` | バンドルの適合性を検証する。ERROR があれば非ゼロ終了 |
| `gulp okf:upgrade` | 仕様バージョンへ追従する（`OKF_VERSION`、`OKF_DRY_RUN=1` で試行） |
| `gulp okf:setup` | 上流ツール `reference_agent` を `tmp/open-knowledge-format` にクローン・インストール |
| `gulp okf:viz` | バンドルをグラフ HTML に可視化する（既定 `tmp/okf/viz.html`） |
| `gulp okf:viz:open` | 可視化 HTML をブラウザで開く |
| `gulp okf:enrich` | BigQuery データセットからバンドルを生成する（要 GCP 認証・`OKF_DATASET`） |
| `gulp okf:help` | タスクと環境変数の一覧 |

環境変数は `.env.example` の OKF セクションを参照してください。

#### 学習データ

記事シリーズ「機械学習から始めるプログラミング入門」の学習データは、書籍『スッキリわかる Python による機械学習入門』の配布 ZIP（`sukkiri-ml-codes.zip`）に含まれます。配布データは書籍購入者のみ利用できるため、リポジトリにはコミットせず、各自の環境で `apps/data/sukkiri-ml/`（`.gitignore` 対象）に配置します。方針は [執筆計画](../article/getting-start-ml/outline.md) の「学習データ」を参照してください。

1. [書籍サポートページ](https://sukkiri.jp/books/sukkiri_ml) から `sukkiri-ml-codes.zip` を入手し、`tmp/` に置く（別の場所に置く場合は `ML_DATA_ZIP` でパスを指定）
2. `gulp data:setup` で学習データを配置する
3. `gulp data:check` で配置を確認する

| コマンド | 概要 |
| :--- | :--- |
| `gulp data:setup` | 配布 ZIP を `tmp/sukkiri-ml/` に展開し、記事で使う 9 ファイルを `apps/data/sukkiri-ml/` に配置する |
| `gulp data:check` | `apps/data/sukkiri-ml/` に学習データが揃っているか確認する。不足があれば非ゼロ終了 |
| `gulp data:help` | タスクと環境変数の一覧 |

その他の運用コマンドは追加予定です。

### インフラ

インフラ構成の一覧を追加予定です。

## 補足

- 現在はカテゴリ索引のみ存在します。
- テンプレートは [template/アプリケーション開発環境セットアップ手順書.md](../template/アプリケーション開発環境セットアップ手順書.md)、[template/開発環境セットアップ手順書.md](../template/開発環境セットアップ手順書.md)、[template/AWSステージング環境セットアップ手順書.md](../template/AWSステージング環境セットアップ手順書.md)、[template/AWSプロダクション環境セットアップ手順書.md](../template/AWSプロダクション環境セットアップ手順書.md) を利用できます。
