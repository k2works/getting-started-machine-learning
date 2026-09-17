---
name: operating-qt
description: SonarQube によるコード品質管理を支援。ローカル SonarQube のセットアップ、スキャン実行、Quality Gate 確認、イシュー分析、メトリクス確認を行う。「SonarQube をセットアップしたい」「コード品質を分析したい」「Quality Gate を確認したい」「静的解析を実行したい」「SonarQube のイシューを確認したい」「コードの重複を調べたい」「カバレッジを SonarQube で見たい」といった場面で発動する。CI/CD パイプラインの品質ゲートチェックにも使用する。
---

# コード品質管理（SonarQube）

SonarQube を使った静的コード解析環境の構築と運用を支援する。SonarQube は Bug・Vulnerability・Code Smell・重複コード・カバレッジを一元的に可視化し、Quality Gate で品質基準を自動判定する。コード品質を継続的に計測することで、技術的負債の蓄積を早期に検知できる。

## Instructions

### 1. 参照ドキュメント

@docs/reference/SonarQubeローカル環境セットアップ手順書.md にセットアップの詳細手順がある。環境構築時はこのドキュメントを参照する。

### 2. 前提条件

- Docker Desktop がインストール済みであること
- RAM 4GB 以上を Docker Desktop に割り当て（推奨 6GB）
- Windows の場合、WSL2 で `vm.max_map_count=524288` が設定済み

### 3. コンポーネント構成

| コンポーネント | コンテナ名 | ポート | イメージ |
|--------------|-----------|-------|---------|
| SonarQube | sonarqube | 9000 | `sonarqube:community` |
| PostgreSQL | sonarqube-db | 内部 | `postgres:16-alpine` |

Docker Compose ファイルは `ops/docker/sonarqube-local/docker-compose.yml` に配置する。

### 4. Gulp タスク

既存の `ops/scripts/sonar_local.js` で以下のタスクが利用可能。

**セットアップ:**

```bash
npx gulp sonar-local:setup      # 初回セットアップ（Docker Compose 配置→起動→ヘルスチェック）
```

**コンテナ操作:**

```bash
npx gulp sonar-local:start      # 起動
npx gulp sonar-local:stop       # 停止
npx gulp sonar-local:restart    # 再起動
npx gulp sonar-local:status     # 状態確認
npx gulp sonar-local:logs       # ログ表示
npx gulp sonar-local:open       # ダッシュボードをブラウザで開く
```

**スキャン・分析:**

```bash
npx gulp sonar-local:scan       # 全プロジェクトのスキャン実行
npx gulp sonar-local:gate       # Quality Gate ステータス確認
npx gulp sonar-local:issues     # メトリクス・イシュー・重複コード詳細
npx gulp sonar-local:check      # スキャン → Quality Gate の一連フロー
```

**管理:**

```bash
npx gulp sonar-local:clean      # 環境完全削除（データ含む）
npx gulp sonar-local:help       # ヘルプ表示
```

### 5. プロジェクト設定

プロジェクトルートに `sonarqube.config.json` を配置して複数プロジェクトのスキャンを設定する。

```json
{
  "projects": [
    {
      "name": "backend",
      "label": "Backend",
      "projectKey": "fleur-memoire-backend",
      "scanType": "sonar-scanner",
      "srcDir": "apps/backend"
    },
    {
      "name": "frontend",
      "label": "Frontend",
      "projectKey": "fleur-memoire-frontend",
      "scanType": "sonar-scanner",
      "srcDir": "apps/frontend"
    }
  ]
}
```

`scanType` は `sonar-scanner`（Node.js）、`sbt`、`maven`、`gradle` に対応。

### 6. 環境変数

`.env` に以下を設定する。

| 変数 | 説明 | デフォルト |
|------|------|----------|
| `LOCAL_SONAR_PORT` | SonarQube ポート | 9000 |
| `LOCAL_SONAR_DB_PASSWORD` | DB パスワード | sonarqube_password |
| `SONAR_HOST_URL` | SonarQube URL | http://localhost:9000 |
| `SONAR_TOKEN` | 分析トークン（スキャン時必須） | — |
| `SONAR_PROJECT_KEY` | Quality Gate / Issues 対象キー | — |

### 7. 初回セットアップフロー

1. `npx gulp sonar-local:setup` でコンテナを構築・起動
2. ブラウザで `http://localhost:9000` にアクセス（初期認証: admin / admin）
3. admin パスワードを変更
4. 分析トークンを生成（My Account → Security → Generate Tokens）
5. `.env` に `SONAR_TOKEN=<生成したトークン>` を追加
6. `sonarqube.config.json` を作成（複数プロジェクト対応時）
7. `npx gulp sonar-local:scan` でスキャン実行

### 8. 品質基準

テスト戦略（@docs/design/test_strategy.md）で定義されたカバレッジ目標と SonarQube の Quality Gate を連携する。

| メトリクス | 目標 |
|-----------|------|
| カバレッジ | ドメイン層 90%、全体 80% |
| 重複率 | 3% 未満 |
| Bug | 0 件 |
| Vulnerability | 0 件 |
| Code Smell | 可能な限り 0 件 |

### 9. CI/CD 連携

GitHub Actions で SonarQube スキャンを実行する場合は `operating-cicd` スキルを参照する。ローカルの SonarQube はあくまで開発中の品質確認用で、CI/CD では SonarCloud や別途ホスティングされた SonarQube を使用する。

### 10. トラブルシューティング

| 症状 | 原因 | 対処 |
|------|------|------|
| コンテナ起動後すぐ停止 | `vm.max_map_count` 不足 | WSL2 / Docker VM で `524288` に設定 |
| ヘルスチェックがタイムアウト | メモリ不足 | Docker Desktop の RAM 割り当てを増やす |
| スキャン時 401 エラー | トークン未設定 or 無効 | `.env` の `SONAR_TOKEN` を確認 |
| `DOCKER_HOST` 接続エラー | 環境変数の干渉 | `cleanDockerEnv()` を使用（`shared.js`） |

### 関連スキル

- `operating-script` — 運用スクリプト作成ガイドに準拠したスクリプト作成
- `operating-cicd` — CI/CD パイプラインでの品質ゲート連携
- `operating-setup` — 環境構築の段階的実行
- `orchestrating-operation` — 運用フェーズ全体のワークフロー
