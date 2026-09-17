---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "前章では静的コード解析ツールとコードフォーマッターを導入しました。"
tags: [article, getting-start-tdd, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

前章では静的コード解析ツールとコードフォーマッターを導入しました。テストの実行、静的解析、フォーマットチェックと、様々なコマンドを使えるようになりましたが、毎回それぞれのコマンドを覚えて実行するのは面倒です。

この章では **タスクランナー** を使ってこれらのタスクをまとめて実行できるようにし、さらに **CI/CD** パイプラインを構築します。

## 6.2 just によるタスク管理

### just とは

> [just](https://github.com/casey/just) は Rust 製のコマンドランナーです。Makefile に似た構文でタスク（レシピ）を定義し、`just <レシピ名>` で実行できます。Makefile と異なりファイルのビルドではなく **コマンド実行に特化** しており、タブ／スペースの混在を許容し、エラーメッセージもわかりやすいのが特徴です。

Ruby の Rake、Java の Gradle、Node の npm scripts、Python の tox、Go プロジェクトでの Makefile に相当します。Rust エコシステムでは Rust 製ということもあり、just がタスクランナーとして広く採用されています。

### justfile の定義

```just
# Rust プロジェクトのタスクランナー

# テスト実行
test:
    cargo test

# Clippy による静的解析
lint:
    cargo clippy -- -D warnings

# 複雑度チェック
complexity:
    cargo clippy -- -D clippy::cognitive_complexity

# コードフォーマット
fmt:
    cargo fmt

# フォーマットチェック
fmt-check:
    cargo fmt --check

# 全チェック実行（フォーマット → 静的解析 → 複雑度 → テスト）
check: fmt-check lint complexity test

# リリースビルド
build:
    cargo build --release

# 実行
run:
    cargo run

# ビルド成果物の削除
clean:
    cargo clean
```

### 主要なタスク

| タスク | コマンド | 説明 |
|--------|---------|------|
| `just test` | `cargo test` | テスト実行 |
| `just lint` | `cargo clippy -- -D warnings` | Clippy による静的解析 |
| `just complexity` | `cargo clippy -- -D clippy::cognitive_complexity` | 複雑度チェック |
| `just fmt` | `cargo fmt` | コードフォーマット |
| `just fmt-check` | `cargo fmt --check` | フォーマットチェック |
| `just check` | fmt-check → lint → complexity → test | 全チェック実行 |
| `just build` | `cargo build --release` | リリースビルド |
| `just clean` | `cargo clean` | ビルド成果物の削除 |

### 実行例

```bash
# 全チェック実行
$ just check
cargo fmt --check
cargo clippy -- -D warnings
cargo clippy -- -D clippy::cognitive_complexity
cargo test
test result: ok. 12 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out
```

## 6.3 GitHub Actions による CI/CD

### CI/CD とは

> CI/CD（Continuous Integration / Continuous Delivery）は、コードの変更を自動的にビルド、テスト、デプロイするプラクティスです。

### ワークフローの定義

`.github/workflows/rust-ci.yml` にワークフローを定義します。

```yaml
name: Rust CI

on:
  push:
    branches: [main, develop]
    paths:
      - "apps/rust/**"
      - ".github/workflows/rust-ci.yml"
  pull_request:
    branches: [main]
    paths:
      - "apps/rust/**"

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
          key: ${{ runner.os }}-nix-rust-${{ hashFiles('flake.lock', 'ops/nix/environments/rust/shell.nix') }}
          restore-keys: |
            ${{ runner.os }}-nix-rust-

      - name: Run all checks
        run: nix develop .#rust --command bash -c "cd apps/rust && just check"
```

### CI パイプラインの流れ

```
Push / PR → just check（fmt-check → lint → complexity → test）→ 結果通知
```

## 6.4 他言語との比較

| 言語 | タスクランナー | CI ツール | テスト | 静的解析 | フォーマット |
|------|-------------|----------|--------|---------|------------|
| Rust | just | GitHub Actions | cargo test | Clippy | rustfmt |
| Go | Makefile | GitHub Actions | go test | golangci-lint | gofmt |
| Java | Gradle | GitHub Actions | JUnit | Checkstyle + PMD | Checkstyle |
| Python | tox | GitHub Actions | pytest | Ruff | Ruff |
| Node | npm scripts | GitHub Actions | Vitest | ESLint | Prettier |
| Ruby | Rake | GitHub Actions | Minitest | RuboCop | RuboCop |
| PHP | Composer scripts | GitHub Actions | PHPUnit | PHP_CodeSniffer + PHPStan | phpcbf |

## 6.5 まとめ

この章では以下を実現しました。

| 項目 | 内容 |
|------|------|
| justfile | test / lint / complexity / fmt / check タスクを定義 |
| `just check` | フォーマットチェック → Clippy → 複雑度 → テストを一括実行 |
| GitHub Actions | push / PR 時に `just check` で CI を実行 |
| Nix 統合 | CI でも `nix develop .#rust` を使用し環境を統一 |

第 2 部を通じて、ソフトウェア開発の三種の神器（バージョン管理、テスティング、自動化）を Rust の開発環境に整備しました。just は Rust 製のコマンドランナーであり、Rust エコシステムとの親和性が高く、Makefile よりもシンプルにタスクを定義できます。次の第 3 部では、オブジェクト指向設計（struct、trait、デザインパターン）に進みます。
