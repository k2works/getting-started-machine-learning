# 機械学習から始めるプログラミング入門

## 概要

### 目的

### 前提

| ソフトウェア | バージョン | 備考 |
| :----------- | :--------- | :--- |
| nodejs       | 22.x / 24.x | 25.x は vitest 非対応のため不可 |
| uv           | 最新       | Python サンプル（`apps/python`） |
| JDK          | 21 以上    | Kotlin サンプル（`apps/kotlin`） |
| .NET SDK     | 10.0.101 以上 | F# サンプル（`apps/fsharp`、`global.json` で固定） |

## 構成

- [構築](#構築)
- [配置](#配置)
- [運用](#運用)
- [開発](#開発)

## 詳細

### Quick Start

```bash
npm install
npm start
```

### 構築

```bash
claude mcp add -s project memory -- npx @modelcontextprotocol/server-memory
claude mcp add -s project codex -- npx @openai/codex mcp-server
```

#### ralph-loopの導入

- Claude Code 起動後、/plugin を実行

- 検索ボックスで ralph-loop を探して選択

- インストールするスコープを選ぶ（ユーザー / プロジェクト / ローカル）

- Claude Code を再起動

- コマンドで実行

```powershell
/ralph-loop "<プロンプト>" --max-iterations <数値> --completion-promise "<完了テキスト>"
```

#### AI アシスタント（Skills）

`.claude/skills/` ディレクトリに定義された Skills により、AI アシスタントがタスクに応じた専門的な指示を自動的に読み込みます。Progressive Disclosure（段階的開示）により、必要なスキルのみがコンテキストに展開されます。

Skills 一覧は [CLAUDE.md の Skills 体系](CLAUDE.md#skills-体系) を参照してください。

新しいスキルの追加・改善には `/skill-creator` プラグインを使用します。テスト・評価・最適化を含むスキル作成ワークフローが自動化されます。

**[⬆ back to top](#構成)**

### 配置

#### GitHub Pages セットアップ

1. **GitHub リポジトリの Settings を開く**
    - リポジトリページで `Settings` タブをクリック

2. **Pages 設定を開く**
    - 左サイドバーの `Pages` をクリック

3. **Source を設定**
    - `Source` で `Deploy from a branch` を選択
    - `Branch` で `gh-pages` を選択し、フォルダは `/ (root)` を選択
    - `Save` をクリック

4. **初回デプロイ**
    - main ブランチにプッシュすると GitHub Actions が自動実行
    - Actions タブでデプロイ状況を確認

**[⬆ back to top](#構成)**

### 運用

#### ドキュメントの編集

1. ローカル環境でMkDocsサーバーを起動
   ```
   docker-compose up mkdocs
   ```
   または、Gulpタスクを使用:
   ```
   npm run docs:serve
   ```

2. ブラウザで http://localhost:8000 にアクセスして編集結果をプレビュー

3. `docs/`ディレクトリ内のMarkdownファイルを編集

4. 変更をコミットしてプッシュ
   ```
   git add .
   git commit -m "ドキュメントの更新"
   git push
   ```

#### Gulpタスクの使用

プロジェクトには以下のGulpタスクが用意されています：

##### MkDocsタスク

- MkDocsサーバーの起動:
  ```
  npm run docs:serve
  ```
  または
  ```
  npx gulp mkdocs:serve
  ```

- MkDocsサーバーの停止:
  ```
  npm run docs:stop
  ```
  または
  ```
  npx gulp mkdocs:stop
  ```

- MkDocsドキュメントのビルド:
  ```
  npm run docs:build
  ```
  または
  ```
  npx gulp mkdocs:build
  ```

##### ユーザーマニュアルタスク

- ユーザーマニュアル（`docs/manual/`）を HTML へ変換:
  ```
  npm run manual:build
  ```
  または
  ```
  npx gulp manual:build
  ```

生成された HTML は `apps/manual/` に出力されます。サイトタイトル・著作権表示・ポータルへの戻りリンクは `.env` の `MANUAL_TITLE` / `MANUAL_COPYRIGHT` / `MANUAL_PORTAL_URL` で設定できます。

##### 作業履歴（ジャーナル）タスク

- すべてのコミット日付の作業履歴を生成:
  ```
  npm run journal
  ```
  または
  ```
  npx gulp journal:generate
  ```

- 特定の日付の作業履歴を生成:
  ```
  npx gulp journal:generate:date --date=YYYY-MM-DD
  ```
  (例: `npx gulp journal:generate:date --date=2023-04-01`)

生成された作業履歴は `docs/journal/` ディレクトリに保存され、各ファイルには指定された日付のコミット情報が含まれます。

#### GitHub Container Registry

このプロジェクトでは、GitHub Container Registry（GHCR）を使用して開発コンテナイメージを管理しています。

##### 自動ビルド・プッシュ

タグをプッシュすると、GitHub Actions が自動的にコンテナイメージをビルドし、GHCR にプッシュします。

```bash
# タグを作成してプッシュ
git tag 0.0.1
git push origin 0.0.1
```

##### イメージの取得・実行

GHCR からイメージを取得して実行するには：

```bash
# イメージをプル
docker pull ghcr.io/k2works/{project_name}:latest

# または特定バージョン
docker pull ghcr.io/k2works/{project_name}:0.0.1

# コンテナを実行
docker run -it -v $(pwd):/srv ghcr.io/k2works/{project_name}:latest
```

または、docker-compose を使用してローカルでビルド・実行することもできます：

```bash
# 開発環境を起動して中に入る
docker-compose run --rm dev bash
```

認証が必要な場合は、以下のコマンドでログインします：

```bash
# GitHub Personal Access Token でログイン
echo $GITHUB_TOKEN | docker login ghcr.io -u <username> --password-stdin
```

##### 権限設定

- リポジトリの Settings → Actions → General で `Read and write permissions` を設定
- `GITHUB_TOKEN` に `packages: write` 権限が付与されています

##### Dev Container の使用

VS Code で Dev Container を使用する場合：

1. VS Code で「Dev Containers: Reopen in Container」を実行
2. または「Dev Containers: Rebuild and Reopen in Container」で再ビルド

**[⬆ back to top](#構成)**

### 開発

#### サンプル実装のセットアップ

記事の各言語版サンプル（`apps/python`・`apps/node`・`apps/kotlin`・`apps/fsharp`）の環境を用意します。

1. **学習データを入手する**
    - [書籍サポートページ](https://sukkiri.jp/books/sukkiri_ml) から `sukkiri-ml-codes.zip` を入手し、`apps/data/` または `tmp/` に置きます（別の場所なら `ML_DATA_ZIP` でパスを指定）。
    - 配布データは書籍購入者のみ利用できます。`apps/data/` は `.gitignore` 対象なのでコミットされません。

2. **セットアップする**
    ```bash
    npm install      # ルートの Gulp などを導入
    npm run setup    # 学習データの配置（未配置時のみ）と全アプリの依存関係インストール
    ```

3. **動作を確認する**
    ```bash
    npm run check    # 学習データの確認と全アプリのテスト・静的解析
    ```

アプリを個別に扱う場合は `npx gulp apps:setup:<python|node|kotlin|fsharp>`・`npx gulp apps:check:<python|node|kotlin|fsharp>` を使います。ローカルのツールが見つからないかバージョンが合わない場合は、Nix が導入されていれば対応する Nix 環境（`nix develop .#python`・`.#node`・`.#kotlin`・`.#dotnet`）の中で自動的に実行します。タスクの一覧は `npx gulp apps:help`、詳細は [運用](docs/operation/index.md) を参照してください。

| npm スクリプト | 内容 |
| :--- | :--- |
| `npm run setup` | 学習データを配置し、全アプリの依存関係をインストールする |
| `npm run check` | 全アプリのテスト・静的解析を実行する |
| `npm run data:setup` | 配布 ZIP を展開し `apps/data/sukkiri-ml/` に学習データを配置する |
| `npm run data:check` | 学習データが揃っているか確認する |

#### Nix による開発環境

Nix を使用して、再現可能な開発環境を構築できます。

##### 準備

1. [Nix をインストール](https://nixos.org/download.html)します。
2. Flakes を有効にします（`~/.config/nix/nix.conf` に `experimental-features = nix-command flakes` を追加）。

##### 環境の利用

- **デフォルト環境（共通ツール）に入る:**
  ```bash
  nix develop
  ```

- **Node.js 環境に入る:**
  ```bash
  nix develop .#node
  ```

- **Python/MkDocs 環境に入る:**
  ```bash
  nix develop .#python
  ```

環境から抜けるには `exit` を入力します。

##### 依存関係の更新

```bash
nix flake update
```

#### GitHub Codespaces に SSH 接続

外部ターミナルアプリから GitHub Codespaces に SSH 接続することで、VS Code のエディタスペースを広く使いながら別ウィンドウのターミナルで作業できます。

##### 前提条件

- [GitHub CLI](https://cli.github.com/) がインストール済みであること

##### 手順

1. **Codespace を作成する**

   https://github.com/codespaces から Codespace を作成します。

2. **Codespace 名を確認する**

   ブラウザに表示される Codespace の URL から名前を取得します。

   例: URL が `https://upgraded-cod-rpxpjr97jrwcxxw7.github.dev/` の場合、Codespace 名は `upgraded-cod-rpxpjr97jrwcxxw7` です。

3. **SSH 接続する**

   ```bash
   gh codespace ssh -c <codespace名>
   ```

   例:
   ```bash
   gh codespace ssh -c upgraded-cod-rpxpjr97jrwcxxw7
   ```

接続後は `npm run build` や `git log` など通常のターミナル操作が可能です。

##### 参考

- [GitHub Codespaces に SSH 接続する](https://zenn.dev/hirokisakabe/articles/fdd7eb730423c0)

**[⬆ back to top](#構成)**

## 参照
