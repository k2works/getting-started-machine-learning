---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "前章では静的コード解析ツールとコードカバレッジを導入しました。"
tags: [article, getting-start-tdd, node]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

前章では静的コード解析ツールとコードカバレッジを導入しました。テストの実行、静的解析、フォーマットチェック、型チェック、カバレッジ計測と、様々なコマンドを使えるようになりましたが、毎回それぞれのコマンドを覚えて実行するのは面倒です。

この章では **タスクランナー** を使ってこれらのタスクをまとめて実行できるようにし、さらに **CI/CD** パイプラインを構築します。

## 6.2 npm scripts

### npm scripts とは

`package.json` の `scripts` セクションでコマンドを定義し、`npm run <script名>` で実行できます。第 1 部から使ってきた `npm test` も npm scripts の機能です。

### 品質チェックスクリプト

```json
{
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage",
    "lint": "eslint src/ test/",
    "lint:fix": "eslint src/ test/ --fix",
    "format": "prettier --write \"src/**/*.ts\" \"test/**/*.ts\"",
    "format:check": "prettier --check \"src/**/*.ts\" \"test/**/*.ts\"",
    "typecheck": "tsc --noEmit",
    "check": "npm run format:check && npm run lint && npm run typecheck && npm test",
    "fix": "npm run format && npm run lint:fix"
  }
}
```

### 主要スクリプトの解説

| スクリプト | 説明 |
|-----------|------|
| `npm test` | テストを実行 |
| `npm run test:watch` | ファイル監視モードでテスト実行 |
| `npm run test:coverage` | テスト実行 + カバレッジレポート |
| `npm run lint` | ESLint による静的解析 |
| `npm run format:check` | Prettier のフォーマットチェック |
| `npm run typecheck` | TypeScript の型チェック |
| `npm run check` | 全品質チェックを一括実行 |
| `npm run fix` | フォーマットと ESLint の自動修正 |

`check` スクリプトは `&&` で複数のコマンドを連結しており、前のコマンドが成功した場合のみ次が実行されます。

## 6.3 タスクランナー — Gulp

npm scripts だけでも基本的なタスク管理は可能ですが、**ファイル監視** や **タスクの組み合わせ** をより柔軟に行うために [Gulp](https://gulpjs.com/) を導入します。

Java の Gradle カスタムタスクや Python の tox に相当します。

### Gulp のインストール

```bash
$ npm install --save-dev gulp gulp-shell
```

### gulpfile.js の設定

```javascript
// gulpfile.js
import { watch, series } from "gulp";
import shell from "gulp-shell";

// 基本タスク
export const test = shell.task(["npm run test"]);
export const coverage = shell.task(["npm run test:coverage"]);
export const lint = shell.task(["npm run lint"]);
export const lintFix = shell.task(["npm run lint:fix"]);
export const format = shell.task(["npm run format"]);
export const formatCheck = shell.task(["npm run format:check"]);
export const typecheck = shell.task(["npm run typecheck"]);

// 複合タスク：リント修正 → フォーマット → テスト
export const checkAndFix = series(lintFix, format, test);

// ファイル監視タスク（Guard 機能）
export function guard() {
  console.log("Guard is watching for file changes...");
  watch("src/**/*.ts", series(lintFix, format, test));
  watch("test/**/*.ts", series(test));
}

// デフォルトタスク
export default series(checkAndFix, guard);
```

### Gulp タスクの実行

```bash
# Guard モード（ファイル監視 + 自動テスト）
$ npm run guard

# 品質チェック + 自動修正
$ npx gulp checkAndFix

# 個別タスクの実行
$ npx gulp test
$ npx gulp lint
$ npx gulp coverage
```

### Guard 機能

`guard` タスクはファイルの変更を監視し、変更があるたびに自動でリント、フォーマット、テストを実行します。

- `src/**/*.ts` の変更 → リント修正 → フォーマット → テスト
- `test/**/*.ts` の変更 → テスト

TDD サイクルでは、Guard を起動した状態でコードを書くと、保存するたびにテストが自動実行され、Red/Green のフィードバックを即座に得られます。

## 6.4 GitHub Actions による CI/CD

プッシュやプルリクエスト時に自動で品質チェックを実行する CI/CD パイプラインを構築します。

### ワークフロー設定

```yaml
# .github/workflows/node-ci.yml
name: Node CI

on:
  push:
    branches: [main, develop]
    paths:
      - "apps/node/**"
      - ".github/workflows/node-ci.yml"
  pull_request:
    branches: [main]
    paths:
      - "apps/node/**"

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout the repository
        uses: actions/checkout@v4

      - name: Install Nix
        uses: cachix/install-nix-action@v30
        with:
          nix_path: nixpkgs=channel:nixos-unstable

      - name: Cache Nix store
        uses: actions/cache@v4
        with:
          path: /tmp/nix-cache
          key: ${{ runner.os }}-nix-node-${{ hashFiles('flake.lock', 'ops/nix/environments/node/shell.nix') }}
          restore-keys: |
            ${{ runner.os }}-nix-node-

      - name: Install dependencies
        run: nix develop .#node --command bash -c "cd apps/node && npm install"

      - name: Run format check
        run: nix develop .#node --command bash -c "cd apps/node && npm run format:check"

      - name: Run lint
        run: nix develop .#node --command bash -c "cd apps/node && npm run lint"

      - name: Run type check
        run: nix develop .#node --command bash -c "cd apps/node && npm run typecheck"

      - name: Run tests
        run: nix develop .#node --command bash -c "cd apps/node && npm test"

      - name: Run coverage
        run: nix develop .#node --command bash -c "cd apps/node && npm run test:coverage"
```

### ワークフローのポイント

| 設定 | 説明 |
|------|------|
| `paths` フィルター | `apps/node/**` に変更があった場合のみ実行 |
| Nix 環境 | `nix develop .#node` で一貫した環境を保証 |
| キャッシュ | Nix ストアをキャッシュして CI を高速化 |
| ステップ分離 | 各チェックを個別ステップで実行し、失敗箇所を特定しやすく |

### 各言語の CI/CD 比較

| 項目 | TypeScript | Java | Python |
|------|-----------|------|--------|
| CI ツール | GitHub Actions | GitHub Actions | GitHub Actions |
| 環境管理 | Nix + npm | Nix + Gradle | Nix + uv |
| テスト | `npm test` | `./gradlew test` | `uv run tox -e test` |
| 品質チェック | `npm run check` | `./gradlew fullCheck` | `uv run tox` |
| タスクランナー | Gulp | Gradle | tox |

## 6.5 開発ワークフローのまとめ

ここまでの設定により、以下の開発ワークフローが確立されました。

### 日常の開発フロー

```
1. Guard を起動（npm run guard）
2. テストを書く（Red）
3. 実装する（Green）→ Guard が自動テスト
4. リファクタリング → Guard が自動テスト
5. コミット（Conventional Commits）
6. プッシュ → CI が自動実行
```

### ツール一覧

| カテゴリ | ツール | 用途 |
|---------|--------|------|
| テスト | Vitest | テスト実行 + カバレッジ |
| パッケージ管理 | npm | 依存関係管理 |
| 静的解析 | ESLint | コード品質チェック |
| フォーマッター | Prettier | コードスタイル統一 |
| 型チェック | TypeScript (tsc) | 静的型チェック |
| タスクランナー | Gulp | タスク自動化 + ファイル監視 |
| CI/CD | GitHub Actions | 継続的インテグレーション |

## 6.6 まとめ

第 2 部（章 4〜6）を通じて、ソフトウェア開発の三種の神器を整備しました。

| 神器 | 導入したもの |
|------|------------|
| バージョン管理 | Git + Conventional Commits |
| テスティング | Vitest + TypeScript + カバレッジ |
| 自動化 | ESLint + Prettier + Gulp + GitHub Actions |

次の第 3 部では、追加仕様を題材にオブジェクト指向設計（カプセル化、ポリモーフィズム、デザインパターン）を学びます。
