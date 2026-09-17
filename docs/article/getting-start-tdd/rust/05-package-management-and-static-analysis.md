---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "前章では Conventional Commits によるコミットメッセージの規約を学びました。"
tags: [article, getting-start-tdd, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前章では Conventional Commits によるコミットメッセージの規約を学びました。この章では、**パッケージ管理** と **静的コード解析** を導入し、コードの品質を自動でチェックできるようにします。

## 5.2 Cargo によるパッケージ管理

### Cargo とは

> Cargo は Rust のパッケージマネージャ兼ビルドシステムです。プロジェクトの作成、ビルド、テスト、依存関係の管理、パッケージの公開まで、Rust 開発のあらゆる面をサポートします。

Java の Gradle、Node の npm（package.json）、Python の uv、Ruby の Bundler（Gemfile）、Go の Go Modules に相当するのが Cargo です。

### Cargo.toml の構成

本プロジェクトの `Cargo.toml` は以下のようになっています。

```toml
[package]
name = "fizzbuzz"
version = "0.1.0"
edition = "2021"
```

Rust の標準ライブラリ（`std::io`、`std::fmt` など）はインポートの `use` 宣言だけで利用でき、`Cargo.toml` への追加は不要です。

### 主要なコマンド

| コマンド | 説明 |
|---------|------|
| `cargo new <name>` | 新しいプロジェクトを作成 |
| `cargo build` | プロジェクトをビルド |
| `cargo test` | テストを実行 |
| `cargo run` | バイナリを実行 |
| `cargo add <crate>` | 依存クレートを追加 |
| `cargo update` | 依存クレートを更新 |

### Cargo の特徴

- **`Cargo.lock` による再現性** — 依存クレートのバージョンを固定し、チーム全員が同じ環境で開発できる
- **ワークスペース** — 複数クレートを 1 つのプロジェクトで管理
- **`target/` ディレクトリ** — Node の `node_modules/`、Python の `.venv/` に相当（`.gitignore` に追加）

## 5.3 Clippy による静的解析

### Clippy とは

> Clippy は Rust の公式リンターです。コードの品質を向上させるための数百のルール（lint）を提供し、一般的なミスやアンチパターンを検出します。

Ruby の RuboCop、Java の Checkstyle + PMD、TypeScript の ESLint、Python の Ruff、Go の golangci-lint に相当するツールです。

### 実行してみる

```bash
$ cargo clippy -- -D warnings
```

`-D warnings` オプションを付けることで、警告をエラーとして扱い、CI で確実にチェックできます。

### Clippy のカテゴリ

| カテゴリ | 説明 |
|---------|------|
| `clippy::correctness` | バグになりうるコード（デフォルト有効） |
| `clippy::style` | 慣用的でないコードスタイル |
| `clippy::complexity` | 不必要に複雑なコード |
| `clippy::perf` | パフォーマンスに影響するコード |
| `clippy::pedantic` | より厳密なチェック（オプトイン） |

## 5.4 rustfmt によるコードフォーマット

### rustfmt とは

> rustfmt は Rust の公式コードフォーマッターです。コードスタイルを統一し、チーム内のスタイル議論を排除します。

Go の gofmt、Python の Ruff format、TypeScript の Prettier、Ruby の RuboCop --auto-correct に相当します。

### 実行してみる

```bash
# フォーマットチェック（CI 向け）
$ cargo fmt --check

# 自動フォーマット
$ cargo fmt
```

### コードスタイル例

rustfmt はデフォルトで以下のスタイルを適用します。

```rust
// Before（手動フォーマット）
fn generate(number:i32)->String{
match(number%3,number%5){(0,0)=>"FizzBuzz".to_string(),(0,_)=>"Fizz".to_string(),(_, 0)=>"Buzz".to_string(),_=>number.to_string(),}
}

// After（rustfmt 適用後）
fn generate(number: i32) -> String {
    match (number % 3, number % 5) {
        (0, 0) => "FizzBuzz".to_string(),
        (0, _) => "Fizz".to_string(),
        (_, 0) => "Buzz".to_string(),
        _ => number.to_string(),
    }
}
```

## 5.5 Clippy によるコード複雑度チェック

### 認知的複雑度（Cognitive Complexity）

Clippy には `cognitive_complexity` lint が内蔵されており、関数の複雑度を計測できます。

> 認知的複雑度とは、コードがどれだけ理解しにくいかを数値化した指標です。循環的複雑度（Cyclomatic Complexity）と異なり、ネストの深さやフロー制御の読みにくさも考慮します。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 1〜7 | 低複雑度: 管理しやすく、問題なし |
| 8〜15 | 中程度の複雑度: リファクタリングを検討 |
| 16〜25 | 高複雑度: リファクタリングが強く推奨される |
| 26 以上 | 非常に高い複雑度: 関数を分割する必要がある |

### clippy.toml による閾値設定

プロジェクトルートに `clippy.toml` を作成し、閾値を設定します。

```toml
cognitive-complexity-threshold = 7
```

他言語の複雑度チェック（PHP の PHPMD `reportLevel: 7`、Python の Ruff `max-complexity = 7`、TypeScript の ESLint `complexity: ["error", { max: 7 }]`）と同じ基準値 **7** を設定します。

### lib.rs での有効化

`lib.rs` にアトリビュートを追加して、プロジェクト全体で複雑度チェックを有効化します。

```rust
#![warn(clippy::cognitive_complexity)]

pub mod application;
pub mod domain;
pub mod fizz_buzz;
```

### 実行してみる

```bash
# 複雑度チェック（警告をエラーとして扱う）
$ cargo clippy -- -D clippy::cognitive_complexity
```

現在の FizzBuzz 実装では、各メソッドが短く単純なため、閾値 7 を超える関数はありません。

### justfile への追加

```just
# 複雑度チェック
complexity:
    cargo clippy -- -D clippy::cognitive_complexity

# 全チェック実行（フォーマット → 静的解析 → 複雑度 → テスト）
check: fmt-check lint complexity test
```

`just complexity` で複雑度チェックを単独実行、`just check` で全品質チェックをまとめて実行できます。

### 他言語との比較

| 言語 | 複雑度チェックツール | 設定 |
|------|---------------------|------|
| Rust | Clippy（cognitive_complexity） | `clippy.toml: cognitive-complexity-threshold = 7` |
| PHP | PHPMD | `reportLevel: 7` |
| Java | PMD | `CyclomaticComplexity` |
| Python | Ruff（McCabe） | `max-complexity = 7` |
| TypeScript | ESLint | `complexity: ["error", { max: 7 }]` |
| Ruby | RuboCop | `Metrics/CyclomaticComplexity` |
| Go | golangci-lint（gocyclo） | デフォルト設定 |

Rust の Clippy は PHP の PHPMD や Go の golangci-lint と異なり、**標準ツールチェーンに内蔵**されているため、追加インストールが不要です。

## 5.6 コードカバレッジ

### cargo-tarpaulin

Rust のカバレッジツールとして `cargo-tarpaulin` があります。

```bash
# インストール（任意）
$ cargo install cargo-tarpaulin

# カバレッジ計測
$ cargo tarpaulin --out stdout
```

!!! note "カバレッジの代替手段"
    cargo-tarpaulin は Linux 環境向けです。macOS では `cargo llvm-cov` が利用できます。Nix 環境ではインストールが必要な場合があるため、テスト網羅率で代替することも可能です。

## 5.7 まとめ

この章では以下を導入しました。

| ツール | 役割 | 他言語の対応ツール |
|--------|------|-------------------|
| Cargo | パッケージ管理・ビルド | npm, Bundler, Go Modules, Gradle |
| Clippy | 静的解析（リンター） | ESLint, RuboCop, golangci-lint, Ruff |
| Clippy（cognitive_complexity） | コード複雑度チェック | PHPMD, PMD, Ruff McCabe, ESLint complexity |
| rustfmt | コードフォーマット | Prettier, gofmt, RuboCop --auto-correct |
| cargo-tarpaulin | カバレッジ計測 | c8, SimpleCov, go test -cover |

次章では、これらのツールを **タスクランナー**（just）でまとめて実行できるようにし、**CI/CD** パイプラインを構築します。
