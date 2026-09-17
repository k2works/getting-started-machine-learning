---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "前章では静的コード解析ツールとコードカバレッジを導入しました。"
tags: [article, getting-start-tdd, ruby]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

前章では静的コード解析ツールとコードカバレッジを導入しました。テストの実行、静的解析、フォーマットチェック、カバレッジ計測と、様々なコマンドを使えるようになりましたが、毎回それぞれのコマンドを覚えて実行するのは面倒です。

この章では **タスクランナー** を使ってこれらのタスクをまとめて実行できるようにし、さらに **CI/CD** パイプラインを構築します。

## 6.2 Rake によるタスク管理

### Rake とは

> Rake は Ruby 版の Make です。Rakefile にタスクを定義し、`rake <タスク名>` で実行できます。Java の Gradle、Node の npm scripts、Python の tox に相当します。

### Rakefile の定義

```ruby
# Rakefile
# frozen_string_literal: true

require 'rake/testtask'

Rake::TestTask.new(:test) do |t|
  t.libs << 'test'
  t.libs << 'lib'
  t.test_files = FileList['test/**/*_test.rb']
end

desc 'Run RuboCop'
task :rubocop do
  sh 'bundle exec rubocop'
end

desc 'Auto-correct RuboCop offenses'
task :format do
  sh 'bundle exec rubocop --auto-correct'
end

desc 'Start Guard'
task :guard do
  sh 'bundle exec guard'
end

desc 'Run all quality checks (rubocop + test)'
task check: %i[rubocop test]

task default: :check
```

### タスク一覧の確認

```bash
$ bundle exec rake -T

rake check    # Run all quality checks (rubocop + test)
rake format   # Auto-correct RuboCop offenses
rake guard    # Start Guard
rake rubocop  # Run RuboCop
rake test     # Run tests
```

### 主要タスクの解説

| タスク | 説明 |
|--------|------|
| `rake test` | Minitest のテスト実行 |
| `rake rubocop` | RuboCop による静的解析 |
| `rake format` | RuboCop による自動フォーマット |
| `rake guard` | Guard の起動（ファイル監視） |
| `rake check` | 全品質チェックを一括実行（rubocop + test） |

`check` タスクは `rubocop` と `test` を依存タスクとして定義しており、両方が成功した場合のみ完了します。

`task default: :check` により、引数なしの `rake` で全品質チェックが実行されます。

```bash
# 以下は同じ結果になる
$ bundle exec rake
$ bundle exec rake check
```

## 6.3 Guard によるファイル監視

### Guard とは

> Guard はファイルの変更を監視し、変更があるたびに指定されたタスクを自動実行するツールです。TDD サイクルでは、コードを保存するたびにテストが自動実行され、Red/Green のフィードバックを即座に得られます。

Node の Gulp（watch 機能）や Java の Gradle Continuous Build（`--continuous`）に相当します。

### Guardfile の設定

```ruby
# Guardfile
# frozen_string_literal: true

guard :minitest, all_on_start: false do
  watch(%r{^lib/(.+)\.rb$}) { |m| "test/#{m[1]}_test.rb" }
  watch(%r{^test/.+_test\.rb$})
  watch(%r{^test/test_helper\.rb$}) { 'test' }
end

guard :rubocop, all_on_start: false do
  watch(/^.*\.rb$/)
  watch(/^\.rubocop\.yml$/) { '.' }
end
```

### Guard の監視ルール

| プラグイン | 監視対象 | アクション |
|-----------|---------|-----------|
| `guard-minitest` | `lib/*.rb` の変更 | 対応するテストファイルを実行 |
| `guard-minitest` | `test/*_test.rb` の変更 | 変更されたテストを実行 |
| `guard-minitest` | `test/test_helper.rb` の変更 | 全テストを実行 |
| `guard-rubocop` | `*.rb` の変更 | RuboCop を実行 |
| `guard-rubocop` | `.rubocop.yml` の変更 | 全ファイルを解析 |

### Guard の起動

```bash
$ bundle exec rake guard
```

Guard を起動した状態でコードを編集すると、保存するたびにテストと RuboCop が自動実行されます。

```
# lib/fizz_buzz.rb を編集して保存すると...
Guard::Minitest is running: test/fizz_buzz_test.rb
6 tests, 12 assertions, 0 failures, 0 errors, 0 skips

# RuboCop も自動実行
Inspecting 1 file
.
1 file inspected, no offenses detected
```

## 6.4 GitHub Actions による CI/CD

プッシュやプルリクエスト時に自動で品質チェックを実行する CI/CD パイプラインを構築します。

### ワークフロー設定

```yaml
# .github/workflows/ruby-ci.yml
name: Ruby CI

on:
  push:
    branches: [main, develop]
    paths:
      - "apps/ruby/**"
      - ".github/workflows/ruby-ci.yml"
  pull_request:
    branches: [main]
    paths:
      - "apps/ruby/**"

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
          key: ${{ runner.os }}-nix-ruby-${{ hashFiles('flake.lock', 'ops/nix/environments/ruby/shell.nix') }}
          restore-keys: |
            ${{ runner.os }}-nix-ruby-

      - name: Install dependencies
        run: nix develop .#ruby --command bash -c "cd apps/ruby && bundle install"

      - name: Run RuboCop
        run: nix develop .#ruby --command bash -c "cd apps/ruby && bundle exec rubocop"

      - name: Run tests
        run: nix develop .#ruby --command bash -c "cd apps/ruby && bundle exec rake test"
```

### ワークフローのポイント

| 設定 | 説明 |
|------|------|
| `paths` フィルター | `apps/ruby/**` に変更があった場合のみ実行 |
| Nix 環境 | `nix develop .#ruby` で一貫した環境を保証 |
| キャッシュ | Nix ストアをキャッシュして CI を高速化 |
| ステップ分離 | 各チェックを個別ステップで実行し、失敗箇所を特定しやすく |

### 各言語の CI/CD 比較

| 項目 | Ruby | Java | TypeScript | Python |
|------|------|------|-----------|--------|
| CI ツール | GitHub Actions | GitHub Actions | GitHub Actions | GitHub Actions |
| 環境管理 | Nix + Bundler | Nix + Gradle | Nix + npm | Nix + uv |
| テスト | `bundle exec rake test` | `./gradlew test` | `npm test` | `uv run tox -e test` |
| 品質チェック | `bundle exec rake check` | `./gradlew fullCheck` | `npm run check` | `uv run tox` |
| タスクランナー | Rake | Gradle | Gulp | tox |

## 6.5 開発ワークフローのまとめ

ここまでの設定により、以下の開発ワークフローが確立されました。

### 日常の開発フロー

```
1. Guard を起動（bundle exec rake guard）
2. テストを書く（Red）
3. 実装する（Green）→ Guard が自動テスト
4. リファクタリング → Guard が自動テスト
5. コミット（Conventional Commits）
6. プッシュ → CI が自動実行
```

### ツール一覧

| カテゴリ | ツール | 用途 |
|---------|--------|------|
| テスト | Minitest | テスト実行 |
| カバレッジ | SimpleCov | ライン + ブランチカバレッジ |
| パッケージ管理 | Bundler | 依存関係管理 |
| 静的解析 | RuboCop | コード品質チェック + フォーマット |
| タスクランナー | Rake | タスク自動化 |
| ファイル監視 | Guard | 変更検知 + 自動実行 |
| CI/CD | GitHub Actions | 継続的インテグレーション |

## 6.6 まとめ

第 2 部（章 4〜6）を通じて、ソフトウェア開発の三種の神器を整備しました。

| 神器 | 導入したもの |
|------|------------|
| バージョン管理 | Git + Conventional Commits |
| テスティング | Minitest + SimpleCov + ブランチカバレッジ |
| 自動化 | RuboCop + Rake + Guard + GitHub Actions |

次の第 3 部では、追加仕様を題材にオブジェクト指向設計（カプセル化、ポリモーフィズム、デザインパターン）を学びます。
