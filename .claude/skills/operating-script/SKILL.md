---
name: operating-script
description: 運用スクリプト（Gulp タスク）の作成・更新を支援。ops/scripts/ 配下に運用スクリプト作成ガイドに準拠したスクリプトを作成し、gulpfile.js に登録する。「Gulp タスクを作りたい」「デプロイスクリプトを作成したい」「運用タスクを自動化したい」「新しい環境の運用スクリプトを追加したい」「開発タスクランナーを更新したい」といった場面で発動する。環境構築完了後に対応する運用スクリプトを作成する場面でも積極的に使用する。
---

# 運用スクリプト作成

環境構築やデプロイ、プロビジョニングなどの運用タスクを自動化する Gulp スクリプトを作成する。スクリプトはプロジェクトの運用効率を左右するため、ネーミング・構造・実装スタイルの一貫性が重要になる。

## Instructions

### 1. 参照ガイド

@docs/reference/運用スクリプト作成ガイド.md に定義されたルールに従う。このガイドがネーミング規則・ディレクトリ構成・コーディング規約の正とする。

### 2. ファイル命名規則

`{カテゴリ}_{環境}.js` の形式で命名する。

| カテゴリ | 説明 | 例 |
|---------|------|-----|
| `develop` | アプリケーション開発タスク | `develop.js` |
| `deploy` | デプロイスクリプト | `deploy_dev.js`, `deploy_stg.js` |
| `provision` | IaC プロビジョニング | `provision_stg.js` |
| `ssh` | SSH・踏み台操作 | `ssh_stg.js` |

環境サフィックス: `_dev`（開発）、`_stg`（ステージング）、`_prd`（本番）、`_local`（ローカル）、なし（環境非依存）

### 3. Gulp タスク命名規則

`{カテゴリ}:{環境}:{アクション}` の形式で命名する。

```
deploy:dev:build          # 開発環境: ビルド
provision:stg:vpc         # ステージング: VPC プロビジョニング
dev:db:start              # アプリ開発: DB 起動
tdd:backend               # TDD モード: バックエンド
```

### 4. スクリプトの基本構造

すべてのスクリプトは以下のセクション構成に従う。

```javascript
'use strict';

import path from 'path';
import { execSync } from 'child_process';
import { cleanDockerEnv } from './shared.js';

// ============================================
// 設定
// ============================================

const PREFIX = 'DEV'; // 環境変数プレフィックス

/** サービス定義 */
const SERVICES = [
  { name: 'backend', port: 8080, label: 'バックエンド' },
];

// ============================================
// ヘルパー関数
// ============================================

/**
 * JSDoc コメントで関数の目的・引数・戻り値を記述
 * @param {string} param - パラメータの説明
 * @returns {string}
 */
function helperFunction(param) {
  // 実装
}

// ============================================
// Gulp タスク
// ============================================

export default function(gulp) {
  gulp.task('category:action', (done) => {
    // タスク実装
    done();
  });

  // ヘルプタスク（必須）
  gulp.task('category:help', (done) => {
    console.log(`...`);
    done();
  });
}
```

### 5. 実装ルール

- **ESM**: `import` / `export` を使用。`require` は使わない
- **strict mode**: ファイル先頭に `'use strict';`
- **JSDoc**: すべての関数に JSDoc コメント
- **DOCKER_HOST 対応**: Docker 操作は `cleanDockerEnv()` を使用
- **ヘルプタスク**: 各カテゴリに `{category}:help` タスクを必ず作成
- **共通関数**: `shared.js` と `ssh.js` の既存関数を活用

### 6. 環境変数

`.env` で管理し、環境プレフィックス（`DEV_`, `STG_`, `PRD_`）で名前空間を分離する。新しい環境変数を追加した場合は `.env.example` も更新する。

### 7. 作成手順

1. カテゴリと環境からファイル名を決定
2. 基本構造テンプレートに従って実装
3. `shared.js` / `ssh.js` の既存関数を活用
4. ヘルプタスクを作成
5. `gulpfile.js` にインポートとタスク登録を追加
6. `npx gulp {category}:help` で動作確認
7. `.env.example` に必要な環境変数を追記

### 8. gulpfile.js への登録

```javascript
import newTasks from './ops/scripts/{new_file}.js';
newTasks(gulp);
```

### 9. 対応する環境とスクリプト

| 環境 | スクリプト | 関連スキル |
|------|----------|----------|
| アプリケーション開発 | `develop.js` | `operating-setup` |
| 開発環境サーバー | `deploy_dev.js` | `operating-deploy` |
| ステージング AWS | `deploy_stg.js`, `provision_stg.js`, `ssh_stg.js` | `operating-deploy`, `operating-provision` |
| 本番 AWS | `deploy_prd.js`, `provision_prd.js`, `ssh_prd.js` | `operating-deploy`, `operating-provision` |

### 10. 注意事項

- 運用スクリプト作成ガイド（@docs/reference/運用スクリプト作成ガイド.md）を正として従う
- Docker 操作では `cleanDockerEnv()` を必ず使う（`DOCKER_HOST` 環境変数による接続エラーを防ぐため）
- 既存スクリプト（`shared.js`, `ssh.js`, `develop.js` 等）のパターンを踏襲する
- 新しいスクリプト作成時は `.env.example` とドキュメントも同時に更新する

### 関連スキル

- `operating-setup` — 環境構築（スクリプト作成のトリガー）
- `operating-deploy` — デプロイ・ロールバック
- `operating-provision` — IaC プロビジョニング
- `operating-cicd` — CI/CD パイプライン構築
- `orchestrating-operation` — 運用フェーズ全体のワークフロー
