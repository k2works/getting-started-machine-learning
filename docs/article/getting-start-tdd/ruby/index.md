# テスト駆動開発から始める Ruby 入門

## 概要

FizzBuzz 問題を題材に、テスト駆動開発（TDD）の基本サイクルから、開発環境の整備、オブジェクト指向設計、関数型プログラミングの活用まで、Ruby の特徴を活かしながら段階的に学びます。

## 対象読者

- Ruby の基本文法を理解しているプログラミング学習者
- TDD を体験してみたい開発者
- オブジェクト指向設計や関数型プログラミングに興味がある方

## 前提条件

- Ruby 3.3 以降がインストールされていること
- Bundler が利用可能であること（Nix 環境推奨: `nix develop .#ruby`）

## Ruby の特徴

| 特徴 | 説明 |
|------|------|
| 動的型付け | 変数に型宣言が不要、柔軟なプログラミングが可能 |
| オブジェクト指向 | すべてがオブジェクト、ダックタイピングによるポリモーフィズム |
| ブロック・Proc・Lambda | 高階関数をサポートする強力なクロージャ機構 |
| Enumerable | コレクション操作のための豊富なメソッド群 |
| オープンクラス | 既存クラスの拡張が可能 |
| パターンマッチング | Ruby 3.0 以降の構造的パターンマッチ |

## 開発ツール

| ツール | 用途 |
|--------|------|
| [Minitest](https://github.com/minitest/minitest) | テスティングフレームワーク |
| [Bundler](https://bundler.io/) | パッケージ管理 |
| [RuboCop](https://rubocop.org/) | リンター・フォーマッター |
| [SimpleCov](https://github.com/simplecov-ruby/simplecov) | カバレッジ計測 |
| [Rake](https://ruby.github.io/rake/) | タスクランナー |

## 目次

### 第 1 部: TDD の基本サイクル

1. [第 1 章: TODO リストと最初のテスト](01-todo-list-and-first-test.md)
2. [第 2 章: 仮実装と三角測量](02-fake-it-and-triangulation.md)
3. [第 3 章: 明白な実装とリファクタリング](03-obvious-implementation-and-refactoring.md)

### 第 2 部: 開発環境と自動化

4. [第 4 章: バージョン管理と Conventional Commits](04-version-control-and-conventional-commits.md)
5. [第 5 章: パッケージ管理と静的解析](05-package-management-and-static-analysis.md)
6. [第 6 章: タスクランナーと CI/CD](06-task-runner-and-ci-cd.md)

### 第 3 部: オブジェクト指向設計

7. [第 7 章: カプセル化とポリモーフィズム](07-encapsulation-and-polymorphism.md)
8. [第 8 章: デザインパターンの適用](08-design-patterns.md)
9. [第 9 章: SOLID 原則とモジュール設計](09-solid-principles-and-module-design.md)

### 第 4 部: 関数型プログラミングへの展開

10. [第 10 章: 高階関数と関数合成](10-higher-order-functions-and-composition.md)
11. [第 11 章: 不変データとパイプライン処理](11-immutable-data-and-pipeline.md)
12. [第 12 章: エラーハンドリングと型安全性](12-error-handling-and-type-safety.md)

## 実装コード

本記事のすべてのコード例は `apps/ruby/` に実装されています。

```bash
# 開発環境に入る
nix develop .#ruby

# テスト実行
cd apps/ruby
bundle exec rake test
```

## 参考文献

- Kent Beck 著『テスト駆動開発』
- Martin Fowler 著『リファクタリング: 既存のコードを安全に改善する』
- Robert C. Martin 著『Clean Code: アジャイルソフトウェア達人の技』
- David Thomas, Andrew Hunt 著『プログラミング Ruby 改訂版』
