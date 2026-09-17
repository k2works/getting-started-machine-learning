# テスト駆動開発から始める PHP 入門

## 概要

FizzBuzz 問題を題材に、テスト駆動開発（TDD）の基本サイクルから、開発環境の整備、オブジェクト指向設計、関数型プログラミングの活用まで、PHP の特徴を活かしながら段階的に学びます。

## 対象読者

- PHP の基本文法を理解しているプログラミング学習者
- TDD を体験してみたい開発者
- オブジェクト指向設計や関数型プログラミングに興味がある方

## 前提条件

- PHP 8.1 以降がインストールされていること
- Composer が利用可能であること（Nix 環境推奨: `nix develop .#php`）

## PHP の特徴

| 特徴 | 説明 |
|------|------|
| 動的型付け + 型宣言 | 型宣言（PHP 7.0+）で段階的に型安全性を向上 |
| オブジェクト指向 | クラス、インターフェース、トレイト、抽象クラスを完備 |
| readonly プロパティ | PHP 8.1+ で不変なプロパティ宣言をサポート |
| enum 型 | PHP 8.1+ でネイティブな列挙型をサポート |
| match 式 | PHP 8.0+ で型安全な条件分岐を提供 |
| アロー関数 | `fn =>` 構文で簡潔な無名関数を記述 |
| コンストラクタプロモーション | PHP 8.0+ でコンストラクタ引数をプロパティに自動昇格 |

## 開発ツール

| ツール | 用途 |
|--------|------|
| [PHPUnit](https://phpunit.de/) | テスティングフレームワーク |
| [Composer](https://getcomposer.org/) | パッケージ管理 |
| [PHP_CodeSniffer](https://github.com/squizlabs/PHP_CodeSniffer) | コーディング規約チェック |
| [PHPStan](https://phpstan.org/) | 静的解析 |
| Composer scripts | タスクランナー |

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

本記事のすべてのコード例は `apps/php/` に実装されています。

```bash
# 開発環境に入る
nix develop .#php

# テスト実行
cd apps/php
composer test
```

## 参考文献

- Kent Beck 著『テスト駆動開発』
- Martin Fowler 著『リファクタリング: 既存のコードを安全に改善する』
- Robert C. Martin 著『Clean Code: アジャイルソフトウェア達人の技』
- [PHP: The Right Way](https://phptherightway.com/)
