---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "前章では Conventional Commits によるコミットメッセージの規約を学びました。"
tags: [article, getting-start-tdd, ruby]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前章では Conventional Commits によるコミットメッセージの規約を学びました。この章では、**パッケージ管理** と **静的コード解析** を導入し、コードの品質を自動でチェックできるようにします。

## 5.2 Bundler によるパッケージ管理

### RubyGems と Bundler

> RubyGems は Ruby のパッケージ管理システムで、ライブラリ（gem）の配布とインストールを行います。Bundler は Gemfile でプロジェクトの依存関係を管理し、バージョンの一貫性を保証します。

第 1 部で Minitest と SimpleCov はすでに導入しています。ここでは、品質管理ツールのための依存関係を含めた Gemfile の全体像を確認します。

### Gemfile

```ruby
# Gemfile
# frozen_string_literal: true

source 'https://rubygems.org'

group :development, :test do
  gem 'guard', '~> 2.18'
  gem 'guard-minitest', '~> 2.4'
  gem 'guard-rubocop', '~> 1.5'
  gem 'guard-shell', '~> 0.7'
  gem 'minitest', '~> 5.25'
  gem 'minitest-reporters', '~> 1.7'
  gem 'rake', '~> 13.2'
  gem 'rubocop', '~> 1.68'
  gem 'simplecov', '~> 0.22'
end
```

依存関係をインストールします。

```bash
$ bundle install
```

### gem の解説

| gem | 用途 |
|-----|------|
| `minitest` | テスティングフレームワーク |
| `minitest-reporters` | テスト結果のフォーマット表示 |
| `rake` | タスクランナー |
| `rubocop` | 静的コード解析 + フォーマッター |
| `simplecov` | コードカバレッジ |
| `guard` | ファイル監視によるタスク自動実行 |
| `guard-minitest` | Guard の Minitest プラグイン |
| `guard-rubocop` | Guard の RuboCop プラグイン |
| `guard-shell` | Guard のシェルコマンドプラグイン |

Java の Gradle、Node の npm、Python の uv に相当するのが Bundler です。`Gemfile.lock` により、チームメンバー全員が同じバージョンの gem を使用できます。

## 5.3 静的コード解析（RuboCop）

### RuboCop とは

> RuboCop は Ruby の静的コード解析ツールです。コーディングスタイルの違反を検出し、一部は自動修正できます。解析とフォーマットの両方の機能を持っています。

Java の Checkstyle + PMD、TypeScript の ESLint、Python の Ruff に相当するツールです。

### .rubocop.yml の設定

```yaml
# .rubocop.yml
AllCops:
  TargetRubyVersion: 3.3
  NewCops: enable
  SuggestExtensions: false

Style/Documentation:
  Enabled: false

Style/FrozenStringLiteralComment:
  Enabled: true

Metrics/MethodLength:
  Max: 20

Metrics/CyclomaticComplexity:
  Max: 7

Metrics/PerceivedComplexity:
  Max: 7

Metrics/BlockLength:
  Exclude:
    - 'test/**/*'
    - 'Rakefile'

Naming/MethodName:
  Enabled: false

Naming/AsciiIdentifiers:
  Exclude:
    - 'test/**/*'
```

### 主要なルールの解説

| ルール | 設定 | 説明 |
|--------|------|------|
| `TargetRubyVersion` | 3.3 | 対象 Ruby バージョン |
| `NewCops: enable` | - | 新しいルールを自動有効化 |
| `Style/FrozenStringLiteralComment` | true | `frozen_string_literal: true` を必須に |
| `Metrics/MethodLength` | Max: 20 | メソッドの最大行数 |
| `Metrics/CyclomaticComplexity` | Max: 7 | 循環的複雑度の上限 |
| `Metrics/PerceivedComplexity` | Max: 7 | 認知的複雑度の上限 |
| `Naming/MethodName` | false | 日本語テストメソッド名を許可 |
| `Naming/AsciiIdentifiers` | 除外 | テストファイルで日本語識別子を許可 |

### RuboCop の実行

```bash
# 解析の実行
$ bundle exec rubocop

# 自動修正
$ bundle exec rubocop --auto-correct
```

実行結果の例:

```bash
$ bundle exec rubocop
Inspecting 6 files
......

6 files inspected, no offenses detected
```

## 5.4 コードフォーマッター

RuboCop は静的解析とコードフォーマットの両方の機能を持っています。TypeScript では ESLint（解析）と Prettier（フォーマット）が別ツールですが、Ruby では RuboCop が両方を担います。

### フォーマットの実行

```bash
# フォーマットチェック（Layout 系ルールのみ）
$ bundle exec rubocop --only Layout

# 自動フォーマット
$ bundle exec rubocop --auto-correct
```

Java の Checkstyle + Spotless、Python の Ruff（フォーマッター機能）に相当します。

## 5.5 コードカバレッジ（SimpleCov）

### SimpleCov とは

> SimpleCov は Ruby のコードカバレッジツールです。テスト実行時にどの行が実行されたかを計測し、HTML レポートを生成します。

Java の JaCoCo、TypeScript の @vitest/coverage-v8、Python の pytest-cov に相当します。

### test_helper.rb の設定

```ruby
# test/test_helper.rb
# frozen_string_literal: true

require 'simplecov'
SimpleCov.start do
  add_filter '/test/'
  enable_coverage :branch
end

require 'minitest/autorun'
require 'minitest/reporters'
Minitest::Reporters.use!
```

- `add_filter '/test/'` — テストファイル自体をカバレッジ対象から除外
- `enable_coverage :branch` — ブランチカバレッジを有効化

### カバレッジの確認

テスト実行時に自動でカバレッジが計測されます。

```bash
$ bundle exec rake test

6 tests, 12 assertions, 0 failures, 0 errors, 0 skips
Coverage report generated for Unit Tests to apps/ruby/coverage.
Line Coverage: 100.0% (8 / 8)
Branch Coverage: 100.0% (6 / 6)
```

HTML レポートは `coverage/index.html` で確認できます。

## 5.6 コード複雑度のチェック

静的コード解析では、コーディング規約だけでなく、**コードの複雑度** もチェックできます。RuboCop の Metrics 系ルールを使って、メソッドの複雑度を制限しましょう。

### 循環的複雑度（Cyclomatic Complexity）

> 循環的複雑度（サイクロマティック複雑度）とは、ソフトウェア測定法の一つであり、コードがどれぐらい複雑であるかをメソッド単位で数値にして表す指標。

本プロジェクトでは、循環的複雑度を **7 以下** に制限しています。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 1〜10 | 低複雑度：管理しやすく、問題なし |
| 11〜20 | 中程度の複雑度：リファクタリングを検討 |
| 21〜50 | 高複雑度：リファクタリングが強く推奨される |
| 51 以上 | 非常に高い複雑度：コードを分割する必要がある |

### 認知的複雑度（Perceived Complexity）

RuboCop では `Metrics/PerceivedComplexity` が認知的複雑度に相当します。コードの構造が「どれだけ頭を使う必要があるか」を定量的に評価します。Java の PMD における `CognitiveComplexity` や TypeScript の ESLint における `complexity` ルールに相当します。

本プロジェクトでは、認知的複雑度を **7 以下** に制限しています。

### .rubocop.yml への設定

```yaml
# .rubocop.yml（複雑度関連の抜粋）
Metrics/CyclomaticComplexity:
  Max: 7

Metrics/PerceivedComplexity:
  Max: 7

Metrics/MethodLength:
  Max: 20
```

### 複雑度チェックの実行

RuboCop の通常実行で複雑度もチェックされます。

```bash
$ bundle exec rubocop
Inspecting 6 files
......

6 files inspected, no offenses detected
```

### 複雑度チェックの効果

コード複雑度の制限により、以下の効果が得られます。

- **可読性向上** — 小さなメソッドは理解しやすい
- **保守性向上** — 変更の影響範囲が限定される
- **テスト容易性** — 個別機能のテストが簡単
- **自動品質管理** — 複雑なコードの混入を自動防止

現在の FizzBuzz の `generate` メソッドは循環的複雑度が 4 で、制限値 7 以内に収まっています。第 3 部でオブジェクト指向設計を進める際も、この制限を意識してコードを書いていきます。

## 5.7 品質チェックの一括実行

すべての品質チェックを一括で実行する `check` タスクを Rakefile に定義しています。

```bash
$ bundle exec rake check
```

このコマンドは以下を順番に実行します。

1. `rubocop` — RuboCop の静的解析 + フォーマットチェック
2. `test` — Minitest のテスト実行（+ SimpleCov カバレッジ）

### 各言語の品質ツール比較

| 用途 | Ruby | Java | TypeScript | Python |
|------|------|------|-----------|--------|
| パッケージ管理 | Bundler | Gradle | npm | uv |
| テスト | Minitest | JUnit 5 | Vitest | pytest |
| 静的解析 | RuboCop | Checkstyle + PMD | ESLint | Ruff |
| フォーマッター | RuboCop | Checkstyle | Prettier | Ruff |
| カバレッジ | SimpleCov | JaCoCo | @vitest/coverage-v8 | pytest-cov |
| 複雑度チェック | RuboCop Metrics | PMD | ESLint complexity | Ruff McCabe |

## 5.8 まとめ

この章では、以下の品質管理ツールを導入しました。

1. **Bundler** — パッケージ管理と依存関係の管理（Gemfile + Gemfile.lock）
2. **RuboCop** — 静的コード解析 + コードフォーマット（`.rubocop.yml` で設定）
3. **SimpleCov** — コードカバレッジの計測（ライン + ブランチカバレッジ）

次の章では、タスクランナーを導入してこれらの品質チェックを自動化し、CI/CD パイプラインを構築します。
