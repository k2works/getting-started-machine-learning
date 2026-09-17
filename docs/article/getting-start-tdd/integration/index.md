# 多言語統合解説

本章では、12 言語（Java, Python, TypeScript, Ruby, Go, PHP, Rust, C#, F#, Clojure, Scala, Elixir, Haskell）による FizzBuzz TDD 実装を横断的に比較し、言語の設計思想、テストフレームワーク、TDD パターン、型システム、開発環境を統合的に解説します。

## 本章の目的

各言語の個別解説では、その言語固有の文法や開発フローに焦点を当てました。本章では視点を上げて、以下の問いに答えます。

- パラダイムの違いは TDD にどのような影響を与えるか
- テストフレームワークの設計は言語の哲学をどう反映しているか
- 型システムの強さはエラーハンドリングにどう影響するか
- Nix による統一開発環境は多言語プロジェクトにどう貢献するか

## 対象言語一覧

| 言語 | テスト FW | ビルドツール | リンター | パラダイム |
|------|----------|-------------|---------|-----------|
| Java | JUnit 5 | Gradle | Checkstyle, PMD | OOP |
| Python | pytest | pip/venv | Ruff | マルチ |
| TypeScript | Vitest | npm | ESLint, Prettier | マルチ |
| Ruby | Minitest | Bundler | RuboCop | OOP/FP |
| Go | testing | go mod | golangci-lint | 構造化 |
| PHP | PHPUnit | Composer | PHP_CodeSniffer, PHPStan, PHPMD | OOP |
| Rust | cargo test | Cargo | Clippy, rustfmt | マルチ |
| C# | xUnit | .NET SDK | dotnet format | OOP |
| F# | xUnit | .NET SDK | FSharpLint | FP/OOP |
| Clojure | clojure.test | Leiningen | Eastwood, Kibit | FP |
| Scala | ScalaTest | sbt | scalafmt, WartRemover | OOP/FP |
| Elixir | ExUnit | Mix | Credo | FP |
| Haskell | HSpec | Stack | HLint | 純粋 FP |

> **Note**: C# と F# は dotnet 環境として 1 つのイテレーションにまとめています。

## 章構成

| 章 | タイトル | 内容 |
|----|---------|------|
| 1 | [12 言語の概要と分類](01-language-overview.md) | パラダイム分類、型システム、ランタイム、FizzBuzz コア実装比較 |
| 2 | [テストフレームワーク比較](02-test-framework-comparison.md) | テスト構造、アサーション、実行コマンドの比較 |
| 3 | [パラダイム別 TDD パターン比較](03-tdd-pattern-comparison.md) | OOP/FP の TDD、ポリモーフィズム、コマンドパターン比較 |
| 4 | [型システムとエラーハンドリング比較](04-type-system-comparison.md) | 静的/動的型付け、Option/Result パターン、型安全性 |
| 5 | [開発環境と CI/CD 比較](05-dev-environment-comparison.md) | Nix 統一環境、ビルド/リンター比較、CI/CD パターン |
| 6 | [学習ロードマップ](06-learning-roadmap.md) | 推奨学習順序、概念マップ、次のステップ |

## 読み方のガイド

本章は以下の 3 通りの読み方を想定しています。

### 通読

第 1 章から順に読み進めることで、12 言語の全体像を俯瞰できます。

### リファレンス

特定のトピック（テストフレームワーク、型システムなど）に興味がある場合は、該当する章を直接参照してください。

### 言語選択の参考

新しい言語を学ぶ際のガイドとして、第 6 章の学習ロードマップから始めることをお勧めします。

## 前提知識

- 少なくとも 1 つの言語の個別解説を読了済み（推奨: Java または Python）
- TDD の基本サイクル（Red-Green-Refactor）の理解
- FizzBuzz 問題の仕様を把握済み

## 凡例

本章のコード例は、各言語の `apps/` ディレクトリにある実装から抽出しています。すべての開発環境は Nix（`nix develop .#{lang}`）で統一されています。
