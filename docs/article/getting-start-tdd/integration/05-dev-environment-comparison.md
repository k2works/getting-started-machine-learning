---
type: Article
title: "開発環境と CI/CD 比較"
description: "本章では、12 言語の開発環境構築、ビルドツール、リンター / フォーマッタ、CI/CD パイプラインを比較します。"
tags: [article, getting-start-tdd, integration]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 開発環境と CI/CD 比較

本章では、12 言語の開発環境構築、ビルドツール、リンター / フォーマッタ、CI/CD パイプラインを比較します。本プロジェクトでは Nix によるすべての言語の開発環境統一を実現しています。

## Nix による統一開発環境アプローチ

### なぜ Nix か

多言語プロジェクトでは、各言語のランタイム、パッケージマネージャ、ツールチェーンのバージョン管理が課題となります。Nix は以下の問題を解決します。

| 課題 | 従来の解決策 | Nix の解決策 |
|------|------------|------------|
| ランタイムバージョン管理 | nvm, pyenv, rbenv 等 | `nix develop` で統一 |
| ツールチェーンの競合 | Docker コンテナ | Nix プロファイルで分離 |
| 再現可能な環境 | Dockerfile + pinning | `flake.lock` で完全再現 |
| CI/CD との一貫性 | CI 設定で個別管理 | 同じ flake を使用 |

### 開発環境の起動

各言語の開発環境は以下のコマンドで起動します。

```bash
# Java 環境
nix develop .#java

# Python 環境
nix develop .#python

# TypeScript 環境
nix develop .#node

# Ruby 環境
nix develop .#ruby

# Go 環境
nix develop .#go

# PHP 環境
nix develop .#php

# Rust 環境
nix develop .#rust

# C# / F# 環境
nix develop .#dotnet

# Clojure 環境
nix develop .#clojure

# Scala 環境
nix develop .#scala

# Elixir 環境
nix develop .#elixir

# Haskell 環境
nix develop .#haskell
```

### Nix Flake の構造

```
ops/nix/
├── flake.nix          # エントリーポイント
├── flake.lock         # 依存関係のロックファイル
└── environments/      # 各言語の開発環境定義
    ├── java.nix
    ├── node.nix
    ├── python.nix
    ├── ruby.nix
    ├── go.nix
    ├── php.nix
    ├── rust.nix
    ├── dotnet.nix
    ├── clojure.nix
    ├── scala.nix
    ├── elixir.nix
    └── haskell.nix
```

## ビルドツール比較表

| 言語 | ビルドツール | 設定ファイル | 依存管理 | タスクランナー |
|------|------------|------------|---------|-------------|
| Java | Gradle | `build.gradle` | Maven Central | Gradle tasks |
| Python | pip / venv | `requirements.txt` / `pyproject.toml` | PyPI | Makefile / Taskfile |
| TypeScript | npm | `package.json` | npm registry | npm scripts |
| Ruby | Bundler | `Gemfile` | RubyGems | Rake |
| Go | go mod | `go.mod` | Go Proxy | Makefile / Taskfile |
| PHP | Composer | `composer.json` | Packagist | Composer scripts |
| Rust | Cargo | `Cargo.toml` | crates.io | cargo / Makefile |
| C# | .NET SDK | `.csproj` | NuGet | dotnet CLI |
| F# | .NET SDK | `.fsproj` | NuGet | dotnet CLI |
| Clojure | Leiningen | `project.clj` | Clojars / Maven | lein tasks |
| Scala | sbt | `build.sbt` | Maven / Ivy | sbt tasks |
| Elixir | Mix | `mix.exs` | Hex | mix tasks |
| Haskell | Stack | `package.yaml` / `stack.yaml` | Stackage / Hackage | stack tasks |

### ビルドコマンド比較

| 言語 | ビルド | クリーン | 実行 |
|------|-------|--------|------|
| Java | `gradle build` | `gradle clean` | `gradle run` |
| Python | - (インタプリタ) | - | `python -m app` |
| TypeScript | `npm run build` | `rm -rf dist` | `npx ts-node src/index.ts` |
| Ruby | - (インタプリタ) | - | `ruby lib/main.rb` |
| Go | `go build` | `go clean` | `go run .` |
| PHP | - (インタプリタ) | - | `php src/main.php` |
| Rust | `cargo build` | `cargo clean` | `cargo run` |
| C# | `dotnet build` | `dotnet clean` | `dotnet run` |
| F# | `dotnet build` | `dotnet clean` | `dotnet run` |
| Clojure | `lein compile` | `lein clean` | `lein run` |
| Scala | `sbt compile` | `sbt clean` | `sbt run` |
| Elixir | `mix compile` | `mix clean` | `mix run` |
| Haskell | `stack build` | `stack clean` | `stack run` |

## リンター / フォーマッタ比較表

### リンター

| 言語 | リンター | 検出対象 | 設定ファイル |
|------|---------|---------|------------|
| Java | Checkstyle | コーディングスタイル | `checkstyle.xml` |
| Java | PMD | バグパターン、コード複雑度 | `pmd-ruleset.xml` |
| Python | Ruff | スタイル + バグ + 複雑度 | `pyproject.toml` |
| TypeScript | ESLint | スタイル + バグパターン | `eslint.config.js` |
| Ruby | RuboCop | スタイル + バグパターン | `.rubocop.yml` |
| Go | golangci-lint | スタイル + バグ + 複雑度 | `.golangci.yml` |
| PHP | PHP_CodeSniffer | コーディングスタイル | `phpcs.xml` |
| PHP | PHPStan | 静的解析（型チェック） | `phpstan.neon` |
| PHP | PHPMD | コード複雑度 | `phpmd.xml` |
| Rust | Clippy | バグパターン + スタイル | `clippy.toml` |
| C# | dotnet format | スタイル | `.editorconfig` |
| F# | FSharpLint | スタイル + 複雑度 | `fsharplint.json` |
| Clojure | Eastwood | バグパターン | `project.clj` |
| Clojure | Kibit | イディオム改善 | - |
| Scala | WartRemover | バグパターン | `build.sbt` |
| Elixir | Credo | スタイル + 複雑度 | `.credo.exs` |
| Haskell | HLint | スタイル + イディオム | `.hlint.yaml` |

### フォーマッタ

| 言語 | フォーマッタ | 設定 |
|------|------------|------|
| Java | Checkstyle（スタイル統一） | `checkstyle.xml` |
| Python | Ruff format | `pyproject.toml` |
| TypeScript | Prettier | `.prettierrc` |
| Ruby | RuboCop（自動修正） | `.rubocop.yml` |
| Go | gofmt / goimports | 設定不要（標準） |
| PHP | PHP_CodeSniffer（phpcbf） | `phpcs.xml` |
| Rust | rustfmt | `rustfmt.toml` |
| C# | dotnet format | `.editorconfig` |
| F# | Fantomas | `.editorconfig` |
| Clojure | cljfmt | `project.clj` |
| Scala | scalafmt | `.scalafmt.conf` |
| Elixir | mix format | `.formatter.exs` |
| Haskell | Ormolu / Fourmolu | 設定不要（標準） |

## CI/CD ワークフロー構成

### GitHub Actions + Nix 共通パターン

すべての言語で共通の CI/CD パターンを採用しています。

```yaml
# .github/workflows/ci.yml（共通パターン）
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        language: [java, python, node, ruby, go, php, rust, dotnet, clojure, scala, elixir, haskell]
    steps:
      - uses: actions/checkout@v4

      - name: Install Nix
        uses: cachix/install-nix-action@v27
        with:
          nix_path: nixpkgs=channel:nixpkgs-unstable

      - name: Run tests
        run: |
          nix develop .#${{ matrix.language }} --command bash -c "
            cd apps/${{ matrix.language }}
            # 言語共通: lint -> test -> coverage
          "
```

### 言語別 CI コマンド

| 言語 | lint | test | coverage |
|------|------|------|---------|
| Java | `gradle checkstyleMain pmdMain` | `gradle test` | `gradle jacocoTestReport` |
| Python | `ruff check .` | `pytest` | `pytest --cov` |
| TypeScript | `npx eslint . && npx prettier --check .` | `npx vitest run` | `npx vitest --coverage` |
| Ruby | `rubocop` | `rake test` | SimpleCov（テスト実行時に自動生成） |
| Go | `golangci-lint run` | `go test ./...` | `go test -coverprofile=coverage.out` |
| PHP | `vendor/bin/phpcs && vendor/bin/phpstan && vendor/bin/phpmd` | `vendor/bin/phpunit` | `vendor/bin/phpunit --coverage-text` |
| Rust | `cargo clippy && cargo fmt --check` | `cargo test` | `cargo tarpaulin` |
| C# | `dotnet format --verify-no-changes` | `dotnet test` | `dotnet test --collect:"XPlat Code Coverage"` |
| F# | `dotnet fsharplint lint` | `dotnet test` | `dotnet test --collect:"XPlat Code Coverage"` |
| Clojure | `lein eastwood && lein kibit` | `lein test` | `lein cloverage` |
| Scala | `sbt scalafmtCheck wartremoverCheck` | `sbt test` | `sbt coverage test coverageReport` |
| Elixir | `mix credo` | `mix test` | `mix test --cover` |
| Haskell | `stack exec -- hlint .` | `stack test` | HPC |

## 品質ゲートの統一

すべての言語で以下の品質ゲートを統一的に適用しています。

### 3 段階の品質ゲート

```
┌──────────┐    ┌──────────┐    ┌──────────┐
│  Lint    │───→│  Test    │───→│ Coverage │
│  (静的)  │    │  (動的)  │    │  (網羅)  │
└──────────┘    └──────────┘    └──────────┘
     │               │               │
     ▼               ▼               ▼
 スタイル統一    全テスト通過    カバレッジ閾値
 バグ検出        回帰テスト      未テスト箇所検出
 複雑度チェック   統合テスト
```

### 品質ゲート対応状況

| 言語 | Lint | 複雑度チェック | テスト | カバレッジ |
|------|------|-------------|-------|-----------|
| Java | Checkstyle + PMD | PMD | JUnit 5 | JaCoCo |
| Python | Ruff | Ruff (McCabe) | pytest | pytest-cov |
| TypeScript | ESLint + Prettier | ESLint (complexity) | Vitest | v8/istanbul |
| Ruby | RuboCop | RuboCop (Metrics) | Minitest | SimpleCov |
| Go | golangci-lint | gocyclo | testing | go cover |
| PHP | PHPCS + PHPStan + PHPMD | PHPMD | PHPUnit | PHPUnit |
| Rust | Clippy + rustfmt | Clippy (cognitive) | cargo test | tarpaulin |
| C# | dotnet format | - | xUnit | coverlet |
| F# | FSharpLint | FSharpLint | xUnit | coverlet |
| Clojure | Eastwood + Kibit | - | clojure.test | cloverage |
| Scala | scalafmt + WartRemover | WartRemover | ScalaTest | scoverage |
| Elixir | Credo | Credo (complexity) | ExUnit | cover |
| Haskell | HLint | - | HSpec | HPC |

## Taskfile による統一タスクランナー

各言語のビルド / テスト / リント コマンドを `Taskfile.yml` で統一的に管理できます。

```yaml
# Taskfile.yml（共通タスク定義）
version: '3'

tasks:
  lint:
    desc: "全言語の lint を実行"
    cmds:
      - task: lint:java
      - task: lint:python
      # ...

  test:
    desc: "全言語のテストを実行"
    cmds:
      - task: test:java
      - task: test:python
      # ...

  test:java:
    dir: apps/java
    cmds:
      - gradle test

  test:python:
    dir: apps/python
    cmds:
      - pytest

  test:node:
    dir: apps/node
    cmds:
      - npx vitest run
```

## 開発ワークフローの統一

### 共通の開発フロー

```
1. nix develop .#{lang}     # 環境起動
2. cd apps/{lang}            # プロジェクトディレクトリに移動
3. {test-command}            # テスト実行（Red）
4. 実装                      # コード修正（Green）
5. {test-command}            # テスト再実行
6. {lint-command}            # リントチェック
7. git commit               # コミット（Refactor 後）
```

### ウォッチモード対応

| 言語 | ウォッチコマンド | 特徴 |
|------|---------------|------|
| Java | `gradle test --continuous` | 変更検知で自動テスト |
| Python | `pytest-watch` | ファイル変更で自動テスト |
| TypeScript | `npx vitest` | デフォルトでウォッチモード |
| Ruby | `guard` | Guardfile で監視設定 |
| Go | `go test -watch` (外部ツール) | air, entr 等 |
| PHP | `phpunit-watcher` | ファイル変更で自動テスト |
| Rust | `cargo watch -x test` | cargo-watch プラグイン |
| C# | `dotnet watch test` | 標準対応 |
| F# | `dotnet watch test` | 標準対応 |
| Clojure | REPL + test | REPL 駆動で即時テスト |
| Scala | `sbt ~test` | チルダ前置で継続実行 |
| Elixir | `mix test --stale` | 変更ファイルのみテスト |
| Haskell | `stack test --file-watch` | ファイル変更で自動テスト |

## まとめ

1. **Nix** により 12 言語の開発環境を `nix develop .#{lang}` の一コマンドで統一的に起動でき、環境構築の手間を大幅に削減しています
2. **ビルドツール**は各言語のエコシステムに最適化されていますが、Taskfile で統一的なインターフェースを提供できます
3. **リンター / フォーマッタ**はすべての言語で導入されており、「lint + complexity + test」の 3 段階品質ゲートを統一的に適用しています
4. **CI/CD** は GitHub Actions + Nix の共通パターンで、matrix strategy により全言語のテストを並列実行できます
5. **ウォッチモード**はほぼすべての言語で対応しており、TDD の Red-Green-Refactor サイクルを高速に回すことができます

次章では、これらの言語をどの順序で学ぶべきかのロードマップを提示します。
