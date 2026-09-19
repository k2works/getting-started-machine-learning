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

1. [書籍サポートページ](https://sukkiri.jp/books/sukkiri_ml) から `sukkiri-ml-codes.zip` を入手し、`tmp/` または `apps/data/` に置く（別の場所に置く場合は `ML_DATA_ZIP` でパスを指定）
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

#### サンプル実装（apps/）

記事の各言語版サンプル（`apps/python`・`apps/node`・`apps/kotlin`・`apps/dotnet`）の環境をまとめて用意します。前提ツール（uv・Node.js 22／24 系・JDK 21 以上・.NET SDK 10.0.101 以上）は `nix develop .#python`・`.#node`・`.#kotlin`・`.#dotnet` で揃います。ローカルのツールが見つからないかバージョンが合わない場合、タスクは Nix が導入されていれば対応する環境の中で自動的に実行します。

| コマンド | 概要 |
| :--- | :--- |
| `gulp apps:setup` | 学習データを配置し（未配置時のみ）、全アプリの依存関係をインストールする |
| `gulp apps:setup:<name>` | 指定アプリ（`python`・`node`・`kotlin`・`fsharp`）の依存関係をインストールする |
| `gulp apps:check` | 学習データを確認し、全アプリのテスト・静的解析を実行する |
| `gulp apps:check:<name>` | 指定アプリのテスト・静的解析を実行する |
| `gulp apps:help` | タスクの一覧 |

`npm run setup`（= `gulp apps:setup`）・`npm run check`（= `gulp apps:check`）・`npm run data:setup`・`npm run data:check` でも呼び出せます。

`gulp apps:check:fsharp` は、`.NET CI`（`.github/workflows/dotnet-ci.yml`）と同じ順に、整形の検査（Fantomas）・静的解析（FSharpLint。ソリューションと `tools/notebooks.fsx`）・Notebook の出力セルの検査・テストを実行します。F# 版の Notebook（`apps/dotnet/notebooks/`）は、`apps/dotnet` で次のコマンドを使って扱います。

| コマンド | 概要 |
| :--- | :--- |
| `dotnet fsi tools/notebooks.fsx verify` | 出力セル・実行番号・実行の記録が残っている Notebook があれば非ゼロ終了（CI と `gulp apps:check:fsharp` で実行） |
| `dotnet fsi tools/notebooks.fsx strip` | 出力セルを消す（コミットの前に実行） |
| `dotnet fsi tools/notebooks.fsx execute` | Notebook を画面なしで実行し、結果を `bin/notebooks/` に書き出す。学習データ・Jupyter・.NET Interactive のカーネルが必要（`jupyter` が PATH に無ければ環境変数 `JUPYTER` で指定）。CI では実行しない |
