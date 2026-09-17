---
type: Reference
title: "Codex CLI MCP サーバー設定手順"
description: "Codex CLI を MCP（Model Context Protocol）サーバーとして動作させ、Claude Code からプロジェクトのコンテキストを効率的に検索・参照できるようにする手順です。"
tags: [reference]
status: stable
generated: { by: human:kakimomokuri, at: 2026-01-19T02:31:36Z }
stale_after: 2027-02-23T00:00:00Z
---

# Codex CLI MCP サーバー設定手順

## 概要

Codex CLI を MCP（Model Context Protocol）サーバーとして動作させ、Claude Code からプロジェクトのコンテキストを効率的に検索・参照できるようにする手順です。

## 前提条件

- Node.js 18 以降がインストールされていること
- npm が利用可能であること
- Claude Code がインストールされていること
- **有効な OpenAI アカウント**があること

## 手順

### 1. Codex CLI のインストール

Codex CLI をグローバルにインストールします。

```bash
npm install -g @openai/codex
```

インストール確認:

```bash
codex --version
```

### 2. OpenAI 認証の設定

Codex CLI を使用するには OpenAI アカウントでの認証が必要です。

#### 方法 1: `codex login` コマンド（推奨）

```bash
codex login
```

ブラウザが開き、OpenAI アカウントでログインできます。認証情報は `~/.codex/auth.json` に保存されます。

#### 方法 2: 環境変数で設定

`.zshrc` または `.bashrc` に追加:

```bash
export OPENAI_API_KEY="sk-your-api-key-here"
```

設定を反映:

```bash
source ~/.zshrc  # または source ~/.bashrc
```

#### 認証状態の確認

```bash
# 認証ファイルの存在確認
ls -la ~/.codex/auth.json

# 簡単な動作テスト
codex exec "Say hello"
```

### 3. Codex MCP サーバーの起動確認

Codex CLI には MCP サーバーとして動作する機能が組み込まれています。以下のコマンドで動作確認します。

```bash
codex mcp-server
```

サーバーが待機状態になることを確認したら `Ctrl+C` で停止します。

**注意**: `codex mcp-server` は stdio transport で動作します。

### 4. Claude Code への MCP サーバー設定

Claude Code の MCP 設定ファイルに Codex MCP サーバーを登録します。

#### 設定ファイルの場所

- **グローバル設定**: `~/.claude/.mcp.json`
- **プロジェクト設定**: `.claude/.mcp.json`

#### 設定内容（推奨: npx 経由）

`.claude/.mcp.json` ファイルの `mcpServers` に以下を追加します:

```json
{
  "mcpServers": {
    "codex": {
      "command": "npx",
      "args": ["@openai/codex", "mcp-server"]
    }
  }
}
```

#### Claude CLI から直接追加する方法（推奨）

```bash
# ユーザーレベル（グローバル）に追加
claude mcp add -s user codex -- npx @openai/codex mcp-server

# プロジェクトレベルに追加
claude mcp add -s project codex -- npx @openai/codex mcp-server
```

**注意**: `npx -y` オプションは Claude Code の MCP 設定では使用できません。

#### グローバルインストール済みの場合

```json
{
  "mcpServers": {
    "codex": {
      "command": "codex",
      "args": ["mcp-server"]
    }
  }
}
```

#### Windows 環境でパスが認識されない場合

フルパスを指定します:

```json
{
  "mcpServers": {
    "codex": {
      "command": "C:\\Users\\<ユーザー名>\\scoop\\shims\\codex.cmd",
      "args": ["mcp-server"]
    }
  }
}
```

### 5. Claude Code からの利用

Claude Code を起動します。

```bash
claude
```

起動後、Claude は MCP 経由で Codex のツールにアクセスできます。

**利用例**:

- 「このプロジェクトの中で認証処理を行っている箇所を探して」
- 「API エンドポイントの一覧を教えて」
- 「User モデルを使用しているファイルを検索して」

## トラブルシューティング

### MCP サーバーが起動しない場合

1. Node.js のバージョンを確認:

   ```bash
   node --version  # 18 以上が必要
   ```

2. Codex CLI の再インストール:

   ```bash
   npm uninstall -g @openai/codex
   npm install -g @openai/codex
   ```

### Claude Code から接続できない場合

1. 設定ファイルの JSON 構文を確認
2. コマンドパスが正しいか確認
3. Claude Code を再起動
4. `/mcp` コマンドで MCP サーバーの状態を確認

### 401 Unauthorized エラーが発生する場合

このエラーは OpenAI 認証に問題がある場合に発生します。

1. **認証ファイルの確認**:

   ```bash
   ls -la ~/.codex/auth.json
   ```

2. **再認証の実行**:

   ```bash
   codex logout
   codex login
   ```

3. **環境変数の確認**:

   ```bash
   echo $OPENAI_API_KEY
   ```

4. **直接テスト**（MCP 経由ではなく CLI で確認）:

   ```bash
   codex exec "Say hello"
   ```

### アカウント無効化エラー（account_deactivated）

以下のエラーが表示される場合:

```
Your OpenAI account has been deactivated, please check your email for more information.
```

**対処方法**:

1. OpenAI からのメールを確認
2. [help.openai.com](https://help.openai.com) でサポートに問い合わせ
3. 別の有効な OpenAI アカウントで再認証:

   ```bash
   codex logout
   codex login
   ```

### MCP 経由で認証情報が引き継がれない場合

MCP サーバーが認証情報を読み取れない場合は、MCP 設定に環境変数を追加:

```json
{
  "mcpServers": {
    "codex": {
      "command": "npx",
      "args": ["@openai/codex", "mcp-server"],
      "env": {
        "OPENAI_API_KEY": "sk-your-api-key-here"
      }
    }
  }
}
```

**注意**: API キーを直接設定ファイルに記載する場合は、`.mcp.json` を `.gitignore` に追加してください。

## 通信確認

セットアップ完了後、以下の方法で Codex MCP との通信を確認できます。

### CLI での確認

```bash
# 直接 Codex CLI で動作確認
codex exec "Say hello"
```

### Claude Code での確認

Claude Code 内で以下のように Codex MCP ツールを呼び出して確認:

```
mcp__codex__codex ツールで "Say hello" を実行してください
```

正常に動作すれば、Codex からの応答が返ってきます。

## ベストプラクティス

1. **プロジェクト固有の設定**: `.claude/settings.json` を使用してプロジェクトごとに設定
2. **除外設定**: `.gitignore` で不要なファイルを除外（Codex は Git を参照）
3. **OpenAI API キー**: `codex login` で認証を設定
4. **セキュリティ**: API キーを含む設定ファイルは `.gitignore` に追加

## Codex MCP ツールの使用方法

Claude Code から Codex MCP サーバーを呼び出す際の主要パラメータを説明します。

### 基本パラメータ

| パラメータ | 説明 | 値の例 |
|-----------|------|--------|
| `prompt` | Codex に実行させるタスクの指示（必須） | `"Create a file named hello"` |
| `sandbox` | 実行環境の権限レベル | `read-only`, `workspace-write`, `danger-full-access` |
| `approval-policy` | コマンド実行時の承認ポリシー | `untrusted`, `on-failure`, `on-request`, `never` |
| `cwd` | 作業ディレクトリ | `C:\path\to\project` |
| `model` | 使用するモデル | `gpt-5.2`, `gpt-5.2-codex` |

### sandbox パラメータ

| 値 | 説明 |
|----|------|
| `read-only` | 読み取り専用（デフォルト）。ファイルの作成・編集不可 |
| `workspace-write` | ワークスペース内のファイル書き込みを許可 |
| `danger-full-access` | 全アクセス許可（注意して使用） |

### approval-policy パラメータ

| 値 | 説明 |
|----|------|
| `untrusted` | すべてのコマンドで承認を要求 |
| `on-failure` | 失敗時のみ承認を要求 |
| `on-request` | リクエスト時に承認を要求 |
| `never` | 承認なしで実行（新規ファイル作成は除く） |

### 使用例

#### ファイル作成

```
prompt: "Create a file named hello containing Hello World"
sandbox: danger-full-access
approval-policy: never
```

**注意**: 新規ファイル作成は `approval-policy: never` でも確認を求められます。

#### コード検索・分析

```
prompt: "Find all files that handle authentication"
sandbox: read-only
```

### 継続的な対話

`codex-reply` ツールを使用して、既存のスレッドで対話を継続できます:

```
threadId: "019bca72-76d5-73a1-9c68-88d1835932a9"
prompt: "はい、作成してください"
```

## 関連リンク

- [Codex CLI GitHub](https://github.com/openai/codex)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Claude Code MCP 設定](https://docs.anthropic.com/claude-code/mcp)
