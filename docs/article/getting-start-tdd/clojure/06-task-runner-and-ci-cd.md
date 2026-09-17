---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "前章では静的コード解析ツールとコード複雑度チェッカーを導入しました。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

前章では静的コード解析ツールとコード複雑度チェッカーを導入しました。テストの実行、静的解析、複雑度チェック、フォーマットチェックと、様々なコマンドを使えるようになりましたが、毎回それぞれのコマンドを覚えて実行するのは面倒です。

この章では **タスクランナー** を使ってこれらのタスクをまとめて実行できるようにし、さらに **Nix** による再現可能な開発環境と **GitHub Actions** による **CI/CD** パイプラインを構築します。

## 6.2 Nix による開発環境

### Nix とは

> Nix は再現可能なビルドと宣言的な環境管理を提供するパッケージマネージャです。`flake.nix` で開発環境を定義すると、チームメンバー全員が同じツールチェインを使えます。

本プロジェクトでは `nix develop .#clojure` コマンドで Clojure 開発に必要なすべてのツールが揃った環境に入れます。

```bash
$ nix develop .#clojure
Clojure development environment activated
  - Clojure: Clojure 1.11.x
  - Leiningen: Leiningen 2.x.x
  - Babashka: x.x.x
  - Clojure LSP: x.x.x
```

### Nix 環境の構成

`ops/nix/environments/clojure/shell.nix` で環境を定義しています。

```nix
{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    clojure
    leiningen
    babashka
    clojure-lsp
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Clojure development environment activated"
  '';
}
```

| パッケージ | 用途 |
|-----------|------|
| `clojure` | Clojure ランタイム（JVM 上） |
| `leiningen` | プロジェクト管理・ビルドツール |
| `babashka` | スクリプティング用 Clojure |
| `clojure-lsp` | Language Server Protocol 実装 |

### Nix を使う利点

- **再現性** — `flake.lock` でバージョンを固定し、誰がいつビルドしても同じ結果
- **分離** — システムの Clojure や Java に依存せず、プロジェクト専用の環境を構築
- **CI 統合** — ローカルと CI で同じ `nix develop` コマンドを使い、環境差異をゼロに

## 6.3 Makefile によるタスク管理

### Makefile とは

> Makefile は Unix 系の定番ビルド/タスク管理ツールである make の設定ファイルです。ターゲット（タスク名）と依存関係、実行コマンドを定義し、`make <ターゲット>` で実行できます。

### Makefile の定義

```makefile
.PHONY: test lint kibit fmt fmt-check complexity bikeshed coverage check build run clean

test:
	lein test

lint:
	lein eastwood

kibit:
	lein kibit

fmt:
	lein cljfmt fix || true

fmt-check:
	lein cljfmt check || true

complexity:
	lein complexity

bikeshed:
	lein bikeshed

coverage:
	lein cloverage

check: lint kibit complexity test

build:
	lein uberjar

run:
	lein run

clean:
	lein clean
```

### 主要なタスク

| タスク | コマンド | 説明 |
|--------|---------|------|
| `make test` | `lein test` | テスト実行 |
| `make lint` | `lein eastwood` | Eastwood による静的解析 |
| `make kibit` | `lein kibit` | Kibit によるイディオム検査 |
| `make complexity` | `lein complexity` | 循環複雑度チェック |
| `make bikeshed` | `lein bikeshed` | コード品質チェック |
| `make fmt` | `lein cljfmt fix` | コードフォーマット |
| `make coverage` | `lein cloverage` | テストカバレッジ計測 |
| `make check` | lint → kibit → complexity → test | 全チェック実行 |

### 実行例

```bash
# Nix 環境に入る
$ nix develop .#clojure

# 全チェック実行
$ make check
lein eastwood
== Warnings: 0. Exceptions thrown: 0
lein kibit
lein complexity
=== Clojure 循環複雑度チェック (閾値: 7) ===
関数数: 10, 違反: 0
複雑度チェック: 成功
lein test
Ran 10 tests containing 38 assertions.
0 failures, 0 errors.
```

## 6.4 GitHub Actions による CI/CD

### CI/CD とは

> CI/CD（Continuous Integration / Continuous Delivery）は、コードの変更を自動的にビルド、テスト、デプロイするプラクティスです。

### ワークフローの定義

`.github/workflows/clojure-ci.yml` にワークフローを定義します。ローカル開発と同じ **Nix** 環境を CI でも使用し、環境差異をゼロにします。

```yaml
name: Clojure CI

on:
  push:
    branches: [main, develop]
    paths:
      - "apps/clojure/**"
      - ".github/workflows/clojure-ci.yml"
  pull_request:
    branches: [main]
    paths:
      - "apps/clojure/**"

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
          key: ${{ runner.os }}-nix-clojure-${{ hashFiles('flake.lock', 'ops/nix/environments/clojure/shell.nix') }}
          restore-keys: |
            ${{ runner.os }}-nix-clojure-

      - name: Run linter (Eastwood)
        run: nix develop .#clojure --command bash -c "cd apps/clojure && lein eastwood"

      - name: Run complexity check
        run: nix develop .#clojure --command bash -c "cd apps/clojure && lein complexity"

      - name: Run tests
        run: nix develop .#clojure --command bash -c "cd apps/clojure && lein test"
```

### CI パイプラインの流れ

```
Push / PR → Checkout → Nix インストール → Nix キャッシュ復元
→ Eastwood 静的解析 → 循環複雑度チェック → テスト実行 → 結果通知
```

### Nix 統合のポイント

1. **`cachix/install-nix-action@v30`** — GitHub Actions ランナーに Nix をインストール
2. **`nix develop .#clojure --command bash -c "..."`** — Nix 環境内でコマンドを実行
3. **Nix ストアキャッシュ** — `flake.lock` と `shell.nix` のハッシュでキャッシュキーを生成し、ビルド時間を短縮
4. **環境の一致** — ローカルの `nix develop .#clojure` と CI の環境が完全に同一

## 6.5 他言語との比較

| 言語 | タスクランナー | CI 環境 | テスト | 静的解析 | 複雑度チェック |
|------|-------------|---------|--------|---------|-------------|
| Clojure | Makefile | Nix | lein test | Eastwood + Kibit | 循環複雑度チェッカー |
| Rust | Makefile | Nix | cargo test | Clippy | Clippy cognitive_complexity |
| Go | Makefile | Nix | go test | golangci-lint | golangci-lint gocyclo |
| Java | Gradle | setup-java | JUnit | Checkstyle + PMD | PMD CyclomaticComplexity |
| Python | tox | Nix | pytest | Ruff | Ruff McCabe |
| Node | npm scripts | Nix | Vitest | ESLint | ESLint complexity |
| Ruby | Rake | Nix | Minitest | RuboCop | RuboCop Metrics |
| PHP | Composer scripts | Nix | PHPUnit | PHP_CodeSniffer + PHPStan | PHPMD |
| C#/F# | Cake | Nix | xUnit | SonarAnalyzer | SonarAnalyzer |

## 6.6 まとめ

この章では以下を実現しました。

| 項目 | 内容 |
|------|------|
| Nix | `nix develop .#clojure` で再現可能な開発環境を構築 |
| Makefile | test / lint / kibit / complexity / check タスクを定義 |
| `make check` | Eastwood → Kibit → 複雑度チェック → テストを一括実行 |
| GitHub Actions | push / PR 時に Nix 環境で自動 CI を実行 |
| Nix 統合 | CI でも `nix develop .#clojure` を使用しローカルと環境を統一 |

第 2 部を通じて、ソフトウェア開発の三種の神器（バージョン管理、テスティング、自動化）を Clojure の開発環境に整備しました。次の第 3 部では、プロトコルとマルチメソッドによるポリモーフィズムに進みます。
